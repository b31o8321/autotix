package dev.autotix.infrastructure.persistence.ticket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.MessageVisibility;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.domain.ticket.TicketType;
import dev.autotix.infrastructure.persistence.ticket.mapper.MessageMapper;
import dev.autotix.infrastructure.persistence.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implements TicketRepository port using MyBatis Plus.
 * Translates between domain Ticket and (TicketEntity + List<MessageEntity>).
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
            TicketId newId = new TicketId(String.valueOf(entity.getId()));
            ticket.assignPersistedId(newId);
            saveNewMessages(entity.getId(), ticket.messages(), Collections.emptyList());
            return newId;
        } else {
            // UPDATE
            ticketMapper.updateById(entity);
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

    /**
     * Returns the MOST RECENT ticket matching (channelId, externalNativeId).
     * The unique constraint on this pair was dropped in Slice 8 to support
     * "spawn new ticket from closed" behaviour.
     */
    @Override
    public Optional<Ticket> findByChannelAndExternalId(ChannelId channelId, String externalNativeId) {
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        qw.eq("channel_id", channelId.value())
          .eq("external_native_id", externalNativeId)
          .orderByDesc("created_at")
          .last("LIMIT 1");
        TicketEntity entity = ticketMapper.selectOne(qw);
        if (entity == null) {
            return Optional.empty();
        }
        List<MessageEntity> messageEntities = loadMessageEntities(entity.getId());
        return Optional.of(toDomain(entity, messageEntities));
    }

    @Override
    public List<Ticket> findSolvedBefore(Instant cutoff) {
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        qw.eq("status", TicketStatus.SOLVED.name())
          .lt("solved_at", cutoff);
        List<TicketEntity> entities = ticketMapper.selectList(qw);
        return entities.stream()
                .map(e -> toDomain(e, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Ticket> findOverdue(java.time.Instant now) {
        // Find tickets where sla_breached = false, status not in terminal set, AND
        // (firstResponseDueAt < now AND firstResponseAt IS NULL)
        // OR (resolutionDueAt < now AND solvedAt IS NULL)
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        qw.eq("sla_breached", false)
          .notIn("status", TicketStatus.SOLVED.name(), TicketStatus.CLOSED.name(), TicketStatus.SPAM.name())
          .and(w -> w
                  .and(inner -> inner
                          .lt("first_response_due_at", now)
                          .isNull("first_response_at"))
                  .or(inner -> inner
                          .lt("resolution_due_at", now)
                          .isNull("solved_at")));
        List<TicketEntity> entities = ticketMapper.selectList(qw);
        return entities.stream()
                .map(e -> toDomain(e, Collections.emptyList()))
                .collect(Collectors.toList());
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

    @Override
    public List<Ticket> findByCustomerId(dev.autotix.domain.customer.CustomerId customerId, int limit) {
        QueryWrapper<TicketEntity> qw = new QueryWrapper<>();
        qw.eq("customer_id", customerId.longValue())
          .orderByDesc("updated_at")
          .last("LIMIT " + limit);
        List<TicketEntity> entities = ticketMapper.selectList(qw);
        return entities.stream()
                .map(e -> toDomain(e, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    @Override
    public Long findLastMessageId(TicketId ticketId) {
        QueryWrapper<MessageEntity> qw = new QueryWrapper<>();
        qw.eq("ticket_id", Long.parseLong(ticketId.value()))
          .orderByDesc("id")
          .last("LIMIT 1");
        MessageEntity me = messageMapper.selectOne(qw);
        return me != null ? me.getId() : null;
    }

    @Override
    public List<Long> findMessageIdsByTicketIdOrdered(TicketId ticketId) {
        QueryWrapper<MessageEntity> qw = new QueryWrapper<>();
        qw.eq("ticket_id", Long.parseLong(ticketId.value()))
          .orderByAsc("occurred_at");
        List<MessageEntity> entities = messageMapper.selectList(qw);
        return entities.stream()
                .map(MessageEntity::getId)
                .collect(Collectors.toList());
    }

    @Override
    public TicketId findTicketIdByEmailMessageId(String emailMessageId) {
        if (emailMessageId == null || emailMessageId.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<MessageEntity> qw = new QueryWrapper<>();
        qw.eq("email_message_id", emailMessageId)
          .orderByDesc("id")
          .last("LIMIT 1");
        MessageEntity me = messageMapper.selectOne(qw);
        if (me == null) {
            return null;
        }
        // Look up ticket entity to get ticket id
        QueryWrapper<MessageEntity> tqw = new QueryWrapper<>();
        tqw.eq("id", me.getId());
        return new TicketId(String.valueOf(me.getTicketId()));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

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
                me.setVisibility(m.visibility() != null ? m.visibility().name() : MessageVisibility.PUBLIC.name());
                me.setEmailMessageId(m.externalMessageId());
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
        e.setSolvedAt(t.solvedAt());
        e.setClosedAt(t.closedAt());
        e.setParentTicketId(t.parentTicketId() != null
                ? Long.parseLong(t.parentTicketId().value()) : null);
        e.setReopenCount(t.reopenCount());
        e.setPriority(t.priority() != null ? t.priority().name() : TicketPriority.NORMAL.name());
        e.setType(t.type() != null ? t.type().name() : TicketType.QUESTION.name());
        // Slice 10: SLA fields
        e.setFirstResponseAt(t.firstResponseAt());
        e.setFirstHumanResponseAt(t.firstHumanResponseAt());
        e.setFirstResponseDueAt(t.firstResponseDueAt());
        e.setResolutionDueAt(t.resolutionDueAt());
        e.setSlaBreached(t.slaBreached());
        // Slice 12: customer + AI suspension + custom fields
        e.setCustomerId(t.customerId() != null ? t.customerId().longValue() : null);
        e.setAiSuspended(t.aiSuspended());
        e.setEscalatedAt(t.escalatedAt());
        Map<String, String> cf = t.customFields();
        // Store "{}" for empty map so MyBatis Plus updateById can clear a previously non-null column
        e.setCustomFieldsJson((cf != null && !cf.isEmpty()) ? JSON.toJSONString(cf) : "{}");
        return e;
    }

    private Ticket toDomain(TicketEntity e, List<MessageEntity> messageEntities) {
        List<Message> messages = new ArrayList<>();
        for (MessageEntity me : messageEntities) {
            MessageVisibility vis = me.getVisibility() != null
                    ? MessageVisibility.valueOf(me.getVisibility())
                    : MessageVisibility.PUBLIC;
            messages.add(new Message(
                    MessageDirection.valueOf(me.getDirection()),
                    me.getAuthor(),
                    me.getContent(),
                    me.getOccurredAt(),
                    vis,
                    me.getEmailMessageId()
            ));
        }
        Set<String> tags = stringToTags(e.getTagsCsv());
        TicketId parentId = e.getParentTicketId() != null
                ? new TicketId(String.valueOf(e.getParentTicketId())) : null;
        int reopenCount = e.getReopenCount() != null ? e.getReopenCount() : 0;
        TicketPriority priority = e.getPriority() != null
                ? TicketPriority.valueOf(e.getPriority()) : TicketPriority.NORMAL;
        TicketType type = e.getType() != null
                ? TicketType.valueOf(e.getType()) : TicketType.QUESTION;
        boolean slaBreached = e.getSlaBreached() != null && e.getSlaBreached();
        // Slice 12
        CustomerId customerId = e.getCustomerId() != null ? new CustomerId(e.getCustomerId()) : null;
        boolean aiSuspended = e.getAiSuspended() != null && e.getAiSuspended();
        Map<String, String> customFields = new HashMap<>();
        if (e.getCustomFieldsJson() != null && !e.getCustomFieldsJson().isEmpty()) {
            customFields = JSON.parseObject(e.getCustomFieldsJson(),
                    new TypeReference<Map<String, String>>() {});
        }
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
                e.getUpdatedAt(),
                e.getSolvedAt(),
                e.getClosedAt(),
                parentId,
                reopenCount,
                priority,
                type,
                e.getFirstResponseAt(),
                e.getFirstHumanResponseAt(),
                e.getFirstResponseDueAt(),
                e.getResolutionDueAt(),
                slaBreached,
                customerId,
                aiSuspended,
                e.getEscalatedAt(),
                customFields
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
