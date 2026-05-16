package dev.autotix.infrastructure.persistence.macro;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.domain.macro.MacroRepository;
import dev.autotix.domain.user.UserRole;
import dev.autotix.infrastructure.persistence.macro.mapper.MacroMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements MacroRepository using MyBatis Plus.
 */
@Repository
public class MacroRepositoryImpl implements MacroRepository {

    private final MacroMapper mapper;

    public MacroRepositoryImpl(MacroMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(Macro macro) {
        MacroEntity entity = toEntity(macro);
        if (macro.id() == null) {
            entity.setId(null);
            mapper.insert(entity);
            macro.assignPersistedId(entity.getId());
        } else {
            mapper.updateById(entity);
        }
        return macro.id();
    }

    @Override
    public Optional<Macro> findById(Long id) {
        MacroEntity entity = mapper.selectById(id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<Macro> findAll() {
        QueryWrapper<MacroEntity> qw = new QueryWrapper<>();
        qw.orderByDesc("usage_count").orderByAsc("name");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Macro> findVisibleTo(UserRole role) {
        if (role == UserRole.ADMIN) {
            return findAll();
        }
        // AGENT: sees AGENT + AI (not ADMIN_ONLY)
        QueryWrapper<MacroEntity> qw = new QueryWrapper<>();
        qw.in("available_to", Arrays.asList(
                MacroAvailability.AGENT.name(),
                MacroAvailability.AI.name()))
          .orderByDesc("usage_count")
          .orderByAsc("name");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private MacroEntity toEntity(Macro m) {
        MacroEntity e = new MacroEntity();
        if (m.id() != null) {
            e.setId(m.id());
        }
        e.setName(m.name());
        e.setBodyMarkdown(m.bodyMarkdown());
        e.setCategory(m.category());
        e.setAvailableTo(m.availableTo().name());
        e.setUsageCount(m.usageCount());
        e.setCreatedAt(m.createdAt() != null ? m.createdAt() : Instant.now());
        e.setUpdatedAt(m.updatedAt() != null ? m.updatedAt() : Instant.now());
        return e;
    }

    private Macro toDomain(MacroEntity e) {
        MacroAvailability availability;
        try {
            availability = MacroAvailability.valueOf(e.getAvailableTo());
        } catch (IllegalArgumentException ex) {
            availability = MacroAvailability.AGENT;
        }
        return Macro.rehydrate(
                e.getId(), e.getName(), e.getBodyMarkdown(),
                e.getCategory(), availability,
                e.getUsageCount(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
