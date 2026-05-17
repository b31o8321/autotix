package dev.autotix.infrastructure.notification;

import dev.autotix.domain.notification.NotificationChannel;
import dev.autotix.domain.notification.NotificationEventKind;
import dev.autotix.domain.notification.NotificationRoute;
import dev.autotix.domain.notification.NotificationRouteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NotificationRouteRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationRouteRepositoryImplTest {

    @Autowired
    NotificationRouteRepository repository;

    @Test
    void save_and_findById_roundTrip() {
        String suffix = String.valueOf(System.currentTimeMillis());
        NotificationRoute route = NotificationRoute.newRoute(
                "email-route-" + suffix,
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.EMAIL,
                "{\"to\":[\"ops@example.com\"],\"subjectTemplate\":\"SLA breach #{externalTicketId}\"}",
                true);

        Long id = repository.save(route);
        assertNotNull(id, "Saved id must not be null");

        Optional<NotificationRoute> found = repository.findById(id);
        assertTrue(found.isPresent());
        NotificationRoute loaded = found.get();
        assertEquals(id, loaded.id());
        assertEquals("email-route-" + suffix, loaded.name());
        assertEquals(NotificationEventKind.SLA_BREACHED, loaded.eventKind());
        assertEquals(NotificationChannel.EMAIL, loaded.channel());
        assertTrue(loaded.enabled());
    }

    @Test
    void findEnabledByEventKind_excludesDisabled() {
        String suffix = String.valueOf(System.currentTimeMillis());

        NotificationRoute enabled = NotificationRoute.newRoute(
                "enabled-slack-" + suffix,
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.SLACK_WEBHOOK,
                "{\"webhookUrl\":\"https://hooks.slack.com/test\"}",
                true);
        NotificationRoute disabled = NotificationRoute.newRoute(
                "disabled-slack-" + suffix,
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.SLACK_WEBHOOK,
                "{\"webhookUrl\":\"https://hooks.slack.com/disabled\"}",
                false);

        Long enabledId = repository.save(enabled);
        Long disabledId = repository.save(disabled);

        List<NotificationRoute> results = repository.findEnabledByEventKind(NotificationEventKind.SLA_BREACHED);

        boolean foundEnabled = results.stream().anyMatch(r -> r.id().equals(enabledId));
        boolean foundDisabled = results.stream().anyMatch(r -> r.id().equals(disabledId));

        assertTrue(foundEnabled, "Enabled route should be returned");
        assertFalse(foundDisabled, "Disabled route should not be returned");
    }

    @Test
    void findAll_includesAllRoutes() {
        String suffix = String.valueOf(System.currentTimeMillis());
        Long id1 = repository.save(NotificationRoute.newRoute(
                "all-1-" + suffix, NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true));
        Long id2 = repository.save(NotificationRoute.newRoute(
                "all-2-" + suffix, NotificationEventKind.SLA_BREACHED, NotificationChannel.SLACK_WEBHOOK, "{}", false));

        List<NotificationRoute> all = repository.findAll();
        assertTrue(all.stream().anyMatch(r -> r.id().equals(id1)));
        assertTrue(all.stream().anyMatch(r -> r.id().equals(id2)));
    }

    @Test
    void delete_removesRoute() {
        String suffix = String.valueOf(System.currentTimeMillis());
        Long id = repository.save(NotificationRoute.newRoute(
                "to-delete-" + suffix, NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true));

        repository.delete(id);

        Optional<NotificationRoute> found = repository.findById(id);
        assertFalse(found.isPresent(), "Deleted route should not be found");
    }

    @Test
    void save_update_roundTrip() {
        String suffix = String.valueOf(System.currentTimeMillis());
        NotificationRoute route = NotificationRoute.newRoute(
                "update-test-" + suffix, NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true);
        Long id = repository.save(route);

        route.updateEnabled(false);
        route.rename("updated-" + suffix);
        repository.save(route);

        NotificationRoute loaded = repository.findById(id)
                .orElseThrow(() -> new AssertionError("Route should be found after update"));
        assertFalse(loaded.enabled());
        assertEquals("updated-" + suffix, loaded.name());
    }
}
