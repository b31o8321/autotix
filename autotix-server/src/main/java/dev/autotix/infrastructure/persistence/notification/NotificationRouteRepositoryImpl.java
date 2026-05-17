package dev.autotix.infrastructure.persistence.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.notification.NotificationChannel;
import dev.autotix.domain.notification.NotificationEventKind;
import dev.autotix.domain.notification.NotificationRoute;
import dev.autotix.domain.notification.NotificationRouteRepository;
import dev.autotix.infrastructure.persistence.notification.mapper.NotificationRouteMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements NotificationRouteRepository using MyBatis Plus against the notification_route table.
 */
@Repository
public class NotificationRouteRepositoryImpl implements NotificationRouteRepository {

    private final NotificationRouteMapper mapper;

    public NotificationRouteRepositoryImpl(NotificationRouteMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long save(NotificationRoute route) {
        NotificationRouteEntity entity = toEntity(route);
        if (route.id() == null) {
            entity.setId(null);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            mapper.insert(entity);
            route.setId(entity.getId());
        } else {
            entity.setUpdatedAt(Instant.now());
            mapper.updateById(entity);
        }
        return route.id();
    }

    @Override
    public Optional<NotificationRoute> findById(Long id) {
        NotificationRouteEntity entity = mapper.selectById(id);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<NotificationRoute> findAll() {
        QueryWrapper<NotificationRouteEntity> qw = new QueryWrapper<>();
        qw.orderByAsc("id");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationRoute> findEnabledByEventKind(NotificationEventKind kind) {
        QueryWrapper<NotificationRouteEntity> qw = new QueryWrapper<>();
        qw.eq("event_kind", kind.name())
          .eq("enabled", true)
          .orderByAsc("id");
        return mapper.selectList(qw).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private NotificationRouteEntity toEntity(NotificationRoute route) {
        NotificationRouteEntity e = new NotificationRouteEntity();
        e.setId(route.id());
        e.setName(route.name());
        e.setEventKind(route.eventKind().name());
        e.setChannel(route.channel().name());
        e.setConfigJson(route.configJson());
        e.setEnabled(route.enabled());
        e.setCreatedAt(route.createdAt());
        e.setUpdatedAt(route.updatedAt());
        return e;
    }

    private NotificationRoute toDomain(NotificationRouteEntity e) {
        return NotificationRoute.rehydrate(
                e.getId(),
                e.getName(),
                NotificationEventKind.valueOf(e.getEventKind()),
                NotificationChannel.valueOf(e.getChannel()),
                e.getConfigJson(),
                e.getEnabled() != null && e.getEnabled(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
