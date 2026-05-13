package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.persistence.ticket.mapper.MessageMapper;
import dev.autotix.infrastructure.persistence.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements TicketRepository port using MyBatis Plus.
 * Translates between domain Ticket and (TicketEntity + List<MessageEntity>).
 * Map status enum <-> string; tags <-> csv; messages handled in separate table.
 */
@Repository
public class TicketRepositoryImpl implements TicketRepository {

    private final TicketMapper ticketMapper;
    private final MessageMapper messageMapper;

    public TicketRepositoryImpl(TicketMapper ticketMapper, MessageMapper messageMapper) {
        this.ticketMapper = ticketMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public TicketId save(Ticket ticket) {
        TicketEntity entity = toEntity(ticket);
        if (ticket.id() == null) {
            // INSERT — clear id so AUTO_INCREMENT kicks in
            entity.setId(null);
            ticketMapper.insert(entity);
            // entity.getId() is populated by MyBatis Plus after insert
            TicketId newId = new TicketId(String.valueOf(entity.getId()));
            ticket.assignPersistedId(newId);
            saveNewMessages(entity.getId(), ticket.messages(), Collections.emptyList());
            return newId;
        } else {
            // UPDATE
            ticketMapper.updateById(entity);
            // Diff messages: load existing, insert only new ones
            Long ticketDbId = Long.parseLong(ticket.id().value());
            List<MessageEntity> existing = loadMessageEntities(ticketDbId);
            saveNewMessages(ticketDbId, ticket.messages(), existing);
            return ticket.id();
        }
    }

    @Override
    public Optional<Ticket> findById(TicketId id) {
        Long dbId = Long.parseLong(id.value());
        TicketEntity entity = ticketMapper.selectById(dbId);
        if (entity == null) {
            return Optional.empty();
        }
        List<MessageEntity> messageEntities = loadMessageEntities(dbId);
        return Optional.of(toDomain(entity, messageEntities));
    }

    @Override
    public Optional<Ticket> findByChannelAndExternalId(ChannelId channelId, String externalNativeId) {
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        qw.eq("channel_id", channelId.value())
          .eq("external_native_id", externalNativeId);
        TicketEntity entity = ticketMapper.selectOne(qw);
        if (entity == null) {
            return Optional.empty();
        }
        List<MessageEntity> messageEntities = loadMessageEntities(entity.getId());
        return Optional.of(toDomain(entity, messageEntities));
    }

    @Override
    public List<Ticket> search(TicketSearchQuery query) {
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        if (query.status != null) {
            qw.eq("status", query.status.name());
        }
        if (query.channelId != null) {
            qw.eq("channel_id", query.channelId.value());
        }
        if (query.assigneeId != null && !query.assigneeId.trim().isEmpty()) {
            qw.eq("assignee_id", query.assigneeId.trim());
        }
        if (query.text != null && !query.text.trim().isEmpty()) {
            qw.and(w -> w.like("subject", query.text.trim()));
        }
        qw.orderByDesc("updated_at");

        int limit = query.limit > 0 ? query.limit : 20;
        int pageNum = (query.offset / limit) + 1;
        Page<TicketEntity> page = new Page<>(pageNum, limit);
        Page<TicketEntity> result = ticketMapper.selectPage(page, qw);

        // list view: messages loaded lazily on detail fetch
        return result.getRecords().stream()
                .map(e -> toDomain(e, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Diff domain messages against already-persisted ones and insert only new rows.
     * Match key: direction + author + content + occurredAt (all must match).
     */
    private void saveNewMessages(Long ticketDbId, List<Message> domainMessages,
                                 List<MessageEntity> existingEntities) {
        Set<String> existingKeys = new HashSet<>();
        for (MessageEntity me : existingEntities) {
            existingKeys.add(messageKey(me.getDirection(), me.getAuthor(),
                    me.getContent(), me.getOccurredAt()));
        }
        for (Message m : domainMessages) {
            String key = messageKey(m.direction().name(), m.author(), m.content(), m.occurredAt());
            if (!existingKeys.contains(key)) {
                MessageEntity me = new MessageEntity();
                me.setTicketId(ticketDbId);
                me.setDirection(m.direction().name());
                me.setAuthor(m.author());
                me.setContent(m.content());
                me.setOccurredAt(m.occurredAt());
                messageMapper.insert(me);
            }
        }
    }

    private String messageKey(String direction, String author, String content, Instant occurredAt) {
        return direction + "|" + author + "|" + occurredAt + "|" + content.hashCode();
    }

    private List<MessageEntity> loadMessageEntities(Long ticketDbId) {
        QueryWrapper<MessageEntity> qw = new QueryWrapper<>();
        qw.eq("ticket_id", ticketDbId).orderByAsc("occurred_at");
        return messageMapper.selectList(qw);
    }

    private TicketEntity toEntity(Ticket t) {
        TicketEntity e = new TicketEntity();
        if (t.id() != null) {
            e.setId(Long.parseLong(t.id().value()));
        }
        e.setChannelId(t.channelId().value());
        e.setExternalNativeId(t.externalNativeId());
        e.setSubject(t.subject());
        e.setCustomerIdentifier(t.customerIdentifier());
        e.setCustomerName(t.customerName());
        e.setAssigneeId(t.assigneeId());
        e.setStatus(t.status().name());
        e.setTagsCsv(tagsToString(t.tags()));
        e.setCreatedAt(t.createdAt());
        e.setUpdatedAt(t.updatedAt());
        return e;
    }

    private Ticket toDomain(TicketEntity e, List<MessageEntity> messageEntities) {
        List<Message> messages = new ArrayList<>();
        for (MessageEntity me : messageEntities) {
            messages.add(new Message(
                    MessageDirection.valueOf(me.getDirection()),
                    me.getAuthor(),
                    me.getContent(),
                    me.getOccurredAt()
            ));
        }
        Set<String> tags = stringToTags(e.getTagsCsv());
        return Ticket.rehydrate(
                new TicketId(String.valueOf(e.getId())),
                new ChannelId(e.getChannelId()),
                e.getExternalNativeId(),
                e.getSubject(),
                e.getCustomerIdentifier(),
                e.getCustomerName(),
                e.getAssigneeId(),
                TicketStatus.valueOf(e.getStatus()),
                messages,
                tags,
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private String tagsToString(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags);
    }

    private Set<String> stringToTags(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(csv.split(",")));
    }
}
