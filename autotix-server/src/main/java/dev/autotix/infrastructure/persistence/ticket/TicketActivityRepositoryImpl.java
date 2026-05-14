package dev.autotix.infrastructure.persistence.ticket;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.persistence.ticket.mapper.TicketActivityMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MyBatis Plus implementation of TicketActivityRepository.
 * Entries are ordered by occurred_at DESC (most recent first).
 */
@Repository
public class TicketActivityRepositoryImpl implements TicketActivityRepository {

    private final TicketActivityMapper mapper;

    public TicketActivityRepositoryImpl(TicketActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(TicketActivity activity) {
        TicketActivityEntity entity = toEntity(activity);
        mapper.insert(entity);
        activity.assignPersistedId(entity.getId());
    }

    @Override
    public List<TicketActivity> findByTicketId(TicketId ticketId, int offset, int limit) {
        Long dbTicketId = Long.parseLong(ticketId.value());
        int safeLimit = limit > 0 ? limit : 100;
        int safeOffset = offset >= 0 ? offset : 0;

        QueryWrapper<TicketActivityEntity> qw = new QueryWrapper<>();
        qw.eq("ticket_id", dbTicketId)
          .orderByDesc("occurred_at")
          .last("LIMIT " + safeLimit + " OFFSET " + safeOffset);

        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private TicketActivityEntity toEntity(TicketActivity a) {
        TicketActivityEntity e = new TicketActivityEntity();
        e.setTicketId(Long.parseLong(a.ticketId().value()));
        e.setActor(a.actor());
        e.setAction(a.action().name());
        e.setDetails(a.details());
        e.setOccurredAt(a.occurredAt());
        return e;
    }

    private TicketActivity toDomain(TicketActivityEntity e) {
        TicketActivity a = new TicketActivity(
                new TicketId(String.valueOf(e.getTicketId())),
                e.getActor(),
                TicketActivityAction.valueOf(e.getAction()),
                e.getDetails(),
                e.getOccurredAt()
        );
        a.assignPersistedId(e.getId());
        return a;
    }
}
