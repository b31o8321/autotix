package dev.autotix.domain.notification;

import java.util.List;
import java.util.Optional;

/**
 * Port for persisting and querying notification routes.
 */
public interface NotificationRouteRepository {

    /** Persist a new route (id is null) or update an existing one. Returns the persisted id. */
    Long save(NotificationRoute route);

    Optional<NotificationRoute> findById(Long id);

    List<NotificationRoute> findAll();

    /** Returns only enabled routes matching the given event kind. */
    List<NotificationRoute> findEnabledByEventKind(NotificationEventKind kind);

    void delete(Long id);
}
