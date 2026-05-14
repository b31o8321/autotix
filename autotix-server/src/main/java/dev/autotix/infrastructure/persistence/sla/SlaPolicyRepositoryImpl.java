package dev.autotix.infrastructure.persistence.sla;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.infrastructure.persistence.sla.mapper.SlaPolicyMapper;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MyBatis Plus implementation of SlaPolicyRepository.
 */
@Repository
public class SlaPolicyRepositoryImpl implements SlaPolicyRepository {

    private final SlaPolicyMapper mapper;

    public SlaPolicyRepositoryImpl(SlaPolicyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String save(SlaPolicy policy) {
        SlaPolicyEntity entity = toEntity(policy);
        if (policy.id() == null) {
            entity.setId(null);
            mapper.insert(entity);
            policy.assignPersistedId(String.valueOf(entity.getId()));
            return policy.id();
        } else {
            mapper.updateById(entity);
            return policy.id();
        }
    }

    @Override
    public Optional<SlaPolicy> findById(String id) {
        SlaPolicyEntity entity = mapper.selectById(Long.parseLong(id));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<SlaPolicy> findByPriority(TicketPriority priority) {
        QueryWrapper<SlaPolicyEntity> qw = new QueryWrapper<>();
        qw.eq("priority", priority.name());
        SlaPolicyEntity entity = mapper.selectOne(qw);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<SlaPolicy> findAllEnabled() {
        QueryWrapper<SlaPolicyEntity> qw = new QueryWrapper<>();
        qw.eq("enabled", true);
        List<SlaPolicyEntity> entities = mapper.selectList(qw);
        List<TicketPriority> priorityOrder = Arrays.asList(TicketPriority.values());
        return entities.stream()
                .map(this::toDomain)
                .sorted(Comparator.comparingInt(p -> priorityOrder.indexOf(p.priority())))
                .collect(Collectors.toList());
    }

    @Override
    public List<SlaPolicy> findAll() {
        List<SlaPolicyEntity> entities = mapper.selectList(new QueryWrapper<>());
        List<TicketPriority> priorityOrder = Arrays.asList(TicketPriority.values());
        return entities.stream()
                .map(this::toDomain)
                .sorted(Comparator.comparingInt(p -> priorityOrder.indexOf(p.priority())))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Mapping
    // -----------------------------------------------------------------------

    private SlaPolicyEntity toEntity(SlaPolicy p) {
        SlaPolicyEntity e = new SlaPolicyEntity();
        if (p.id() != null) {
            e.setId(Long.parseLong(p.id()));
        }
        e.setName(p.name());
        e.setPriority(p.priority().name());
        e.setFirstResponseMinutes(p.firstResponseMinutes());
        e.setResolutionMinutes(p.resolutionMinutes());
        e.setEnabled(p.enabled());
        e.setCreatedAt(p.createdAt());
        e.setUpdatedAt(p.updatedAt());
        return e;
    }

    private SlaPolicy toDomain(SlaPolicyEntity e) {
        return SlaPolicy.rehydrate(
                String.valueOf(e.getId()),
                e.getName(),
                TicketPriority.valueOf(e.getPriority()),
                e.getFirstResponseMinutes() != null ? e.getFirstResponseMinutes() : 240,
                e.getResolutionMinutes() != null ? e.getResolutionMinutes() : 1440,
                e.getEnabled() != null && e.getEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
