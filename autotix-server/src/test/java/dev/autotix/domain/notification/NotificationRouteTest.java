package dev.autotix.domain.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NotificationRoute factory and mutators.
 */
class NotificationRouteTest {

    @Test
    void newRoute_setsAllFieldsAndTimestamps() {
        NotificationRoute route = NotificationRoute.newRoute(
                "My Alert",
                NotificationEventKind.SLA_BREACHED,
                NotificationChannel.EMAIL,
                "{\"to\":[\"ops@example.com\"]}",
                true);

        assertNull(route.id(), "id must be null before persistence");
        assertEquals("My Alert", route.name());
        assertEquals(NotificationEventKind.SLA_BREACHED, route.eventKind());
        assertEquals(NotificationChannel.EMAIL, route.channel());
        assertEquals("{\"to\":[\"ops@example.com\"]}", route.configJson());
        assertTrue(route.enabled());
        assertNotNull(route.createdAt());
        assertNotNull(route.updatedAt());
    }

    @Test
    void rehydrate_setsAllFields() {
        java.time.Instant ts = java.time.Instant.parse("2026-01-01T00:00:00Z");
        NotificationRoute route = NotificationRoute.rehydrate(
                42L, "Slack SLA", NotificationEventKind.SLA_BREACHED,
                NotificationChannel.SLACK_WEBHOOK, "{\"webhookUrl\":\"https://hooks.slack.com/x\"}", false, ts, ts);

        assertEquals(42L, route.id());
        assertEquals("Slack SLA", route.name());
        assertEquals(NotificationChannel.SLACK_WEBHOOK, route.channel());
        assertFalse(route.enabled());
        assertEquals(ts, route.createdAt());
    }

    @Test
    void rename_updatesNameAndUpdatedAt() throws InterruptedException {
        NotificationRoute route = NotificationRoute.newRoute(
                "Old Name", NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true);
        java.time.Instant before = route.updatedAt();
        Thread.sleep(5);
        route.rename("New Name");
        assertEquals("New Name", route.name());
        assertTrue(route.updatedAt().isAfter(before));
    }

    @Test
    void updateConfig_changesConfigJson() {
        NotificationRoute route = NotificationRoute.newRoute(
                "R", NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true);
        route.updateConfig("{\"to\":[\"new@example.com\"]}");
        assertEquals("{\"to\":[\"new@example.com\"]}", route.configJson());
    }

    @Test
    void updateEnabled_toggles() {
        NotificationRoute route = NotificationRoute.newRoute(
                "R", NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true);
        route.updateEnabled(false);
        assertFalse(route.enabled());
        route.updateEnabled(true);
        assertTrue(route.enabled());
    }

    @Test
    void applyUpdate_changesAllMutableFields() {
        NotificationRoute route = NotificationRoute.newRoute(
                "Old", NotificationEventKind.SLA_BREACHED, NotificationChannel.EMAIL, "{}", true);
        route.applyUpdate("New", NotificationEventKind.SLA_BREACHED, NotificationChannel.SLACK_WEBHOOK,
                "{\"webhookUrl\":\"https://x\"}", false);
        assertEquals("New", route.name());
        assertEquals(NotificationChannel.SLACK_WEBHOOK, route.channel());
        assertEquals("{\"webhookUrl\":\"https://x\"}", route.configJson());
        assertFalse(route.enabled());
    }
}
