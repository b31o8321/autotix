package dev.autotix.domain.sla;

import dev.autotix.domain.ticket.TicketPriority;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for the SlaPolicy value object.
 */
class SlaPolicyTest {

    @Test
    void create_setsAllFields() {
        SlaPolicy policy = SlaPolicy.create("High Priority SLA", TicketPriority.HIGH, 60, 480, true);

        assertNull(policy.id()); // no id until persisted
        assertEquals("High Priority SLA", policy.name());
        assertEquals(TicketPriority.HIGH, policy.priority());
        assertEquals(60, policy.firstResponseMinutes());
        assertEquals(480, policy.resolutionMinutes());
        assertTrue(policy.enabled());
        assertNotNull(policy.createdAt());
        assertNotNull(policy.updatedAt());
    }

    @Test
    void rehydrate_restoresAllFields() {
        Instant createdAt = Instant.parse("2025-01-01T00:00:00Z");
        Instant updatedAt = Instant.parse("2025-06-01T00:00:00Z");

        SlaPolicy policy = SlaPolicy.rehydrate(
                "42", "Normal SLA", TicketPriority.NORMAL, 240, 1440, true, createdAt, updatedAt);

        assertEquals("42", policy.id());
        assertEquals("Normal SLA", policy.name());
        assertEquals(TicketPriority.NORMAL, policy.priority());
        assertEquals(240, policy.firstResponseMinutes());
        assertEquals(1440, policy.resolutionMinutes());
        assertTrue(policy.enabled());
        assertEquals(createdAt, policy.createdAt());
        assertEquals(updatedAt, policy.updatedAt());
    }

    @Test
    void update_changesFields() {
        SlaPolicy policy = SlaPolicy.create("Old Name", TicketPriority.URGENT, 30, 240, true);

        policy.update("New Name", 15, 120, false);

        assertEquals("New Name", policy.name());
        assertEquals(15, policy.firstResponseMinutes());
        assertEquals(120, policy.resolutionMinutes());
        assertFalse(policy.enabled());
    }

    @Test
    void assignPersistedId_setsId() {
        SlaPolicy policy = SlaPolicy.create("Low SLA", TicketPriority.LOW, 480, 2880, true);
        assertNull(policy.id());

        policy.assignPersistedId("99");

        assertEquals("99", policy.id());
    }
}
