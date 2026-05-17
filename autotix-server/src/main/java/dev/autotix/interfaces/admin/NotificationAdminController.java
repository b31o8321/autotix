package dev.autotix.interfaces.admin;

import dev.autotix.domain.notification.NotificationChannel;
import dev.autotix.domain.notification.NotificationEventKind;
import dev.autotix.domain.notification.NotificationRoute;
import dev.autotix.domain.notification.NotificationRouteRepository;
import dev.autotix.infrastructure.notification.NotificationDispatcher;
import dev.autotix.interfaces.admin.dto.NotificationRouteDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for notification route CRUD and test-fire.
 *
 * GET    /api/admin/notifications/routes            — list all routes
 * POST   /api/admin/notifications/routes            — create route
 * PUT    /api/admin/notifications/routes/{id}       — update route
 * DELETE /api/admin/notifications/routes/{id}       — delete route
 * GET    /api/admin/notifications/routes/test/{id}  — fire a synthetic event to verify route
 */
@RestController
@RequestMapping("/api/admin/notifications/routes")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationAdminController {

    private static final DateTimeFormatter ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final NotificationRouteRepository routeRepository;
    private final NotificationDispatcher notificationDispatcher;

    public NotificationAdminController(NotificationRouteRepository routeRepository,
                                       NotificationDispatcher notificationDispatcher) {
        this.routeRepository = routeRepository;
        this.notificationDispatcher = notificationDispatcher;
    }

    @GetMapping
    public List<NotificationRouteDTO> list() {
        return routeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public NotificationRouteDTO create(@RequestBody NotificationRouteDTO dto) {
        NotificationRoute route = NotificationRoute.newRoute(
                dto.name,
                parseEventKind(dto.eventKind),
                parseChannel(dto.channel),
                dto.configJson,
                dto.enabled);
        Long id = routeRepository.save(route);
        dto.id = id;
        dto.createdAt = ISO_FMT.format(route.createdAt());
        dto.updatedAt = ISO_FMT.format(route.updatedAt());
        return dto;
    }

    @PutMapping("/{id}")
    public NotificationRouteDTO update(@PathVariable Long id, @RequestBody NotificationRouteDTO dto) {
        NotificationRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id));
        route.applyUpdate(
                dto.name,
                parseEventKind(dto.eventKind),
                parseChannel(dto.channel),
                dto.configJson,
                dto.enabled);
        routeRepository.save(route);
        return toDTO(route);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id));
        routeRepository.delete(id);
    }

    /**
     * Fire a synthetic SLA_BREACHED event for this route (regardless of its eventKind)
     * using dummy data, so the operator can verify connectivity.
     *
     * Returns a simple result object.
     */
    @GetMapping("/test/{id}")
    public Map<String, Object> test(@PathVariable Long id) {
        NotificationRoute route = routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found: " + id));

        Map<String, String> ctx = new HashMap<>();
        ctx.put("ticketId", "TEST-1");
        ctx.put("externalTicketId", "EXT-TEST-1");
        ctx.put("subject", "[TEST] SLA breach notification test");
        ctx.put("customerIdentifier", "test@example.com");
        ctx.put("priority", "HIGH");
        ctx.put("status", "OPEN");
        ctx.put("breachedAt", ISO_FMT.format(Instant.now()));
        ctx.put("ticketUrl", "http://localhost/inbox?ticket=TEST-1");

        // Temporarily enable route if it's disabled so test always fires
        boolean wasEnabled = route.enabled();
        route.updateEnabled(true);
        try {
            notificationDispatcher.dispatch(route.eventKind(), ctx);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("routeId", id);
            result.put("channel", route.channel().name());
            result.put("message", "Test notification dispatched. Check your " + route.channel().name() + " destination.");
            return result;
        } finally {
            if (!wasEnabled) {
                route.updateEnabled(false);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private NotificationRouteDTO toDTO(NotificationRoute route) {
        NotificationRouteDTO dto = new NotificationRouteDTO();
        dto.id = route.id();
        dto.name = route.name();
        dto.eventKind = route.eventKind().name();
        dto.channel = route.channel().name();
        dto.configJson = route.configJson();
        dto.enabled = route.enabled();
        if (route.createdAt() != null) dto.createdAt = ISO_FMT.format(route.createdAt());
        if (route.updatedAt() != null) dto.updatedAt = ISO_FMT.format(route.updatedAt());
        return dto;
    }

    private static NotificationEventKind parseEventKind(String s) {
        try {
            return NotificationEventKind.valueOf(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown eventKind: " + s);
        }
    }

    private static NotificationChannel parseChannel(String s) {
        try {
            return NotificationChannel.valueOf(s);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown channel: " + s);
        }
    }
}
