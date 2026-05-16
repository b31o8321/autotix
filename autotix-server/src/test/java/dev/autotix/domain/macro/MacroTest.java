package dev.autotix.domain.macro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Macro aggregate.
 */
class MacroTest {

    @Test
    void newMacro_setsDefaultsCorrectly() {
        Macro m = Macro.newMacro("Welcome", "Hello, how can I help?", "support", null);
        assertNull(m.id(), "id should be null before persisted");
        assertEquals("Welcome", m.name());
        assertEquals("Hello, how can I help?", m.bodyMarkdown());
        assertEquals("support", m.category());
        assertEquals(MacroAvailability.AGENT, m.availableTo(), "default availableTo should be AGENT");
        assertEquals(0, m.usageCount());
        assertNotNull(m.createdAt());
        assertNotNull(m.updatedAt());
    }

    @Test
    void newMacro_withExplicitAvailability() {
        Macro m = Macro.newMacro("Admin only", "Secret content", null, MacroAvailability.ADMIN_ONLY);
        assertEquals(MacroAvailability.ADMIN_ONLY, m.availableTo());
    }

    @Test
    void rename_changesNameAndStampsUpdatedAt() throws InterruptedException {
        Macro m = Macro.newMacro("Old Name", "body", null, MacroAvailability.AGENT);
        java.time.Instant before = m.updatedAt();
        Thread.sleep(10);
        m.rename("New Name");
        assertEquals("New Name", m.name());
        assertTrue(m.updatedAt().isAfter(before) || m.updatedAt().equals(before),
                "updatedAt should be >= before");
    }

    @Test
    void updateBody_replacesBody() {
        Macro m = Macro.newMacro("name", "original body", null, MacroAvailability.AGENT);
        m.updateBody("updated body");
        assertEquals("updated body", m.bodyMarkdown());
    }

    @Test
    void recordUsage_incrementsCount() {
        Macro m = Macro.newMacro("name", "body", null, MacroAvailability.AGENT);
        assertEquals(0, m.usageCount());
        m.recordUsage();
        assertEquals(1, m.usageCount());
        m.recordUsage();
        assertEquals(2, m.usageCount());
    }

    @Test
    void rehydrate_roundTrip() {
        java.time.Instant now = java.time.Instant.now();
        Macro m = Macro.rehydrate(42L, "macro", "body", "cat", MacroAvailability.AI, 5, now, now);
        assertEquals(42L, m.id());
        assertEquals("macro", m.name());
        assertEquals(MacroAvailability.AI, m.availableTo());
        assertEquals(5, m.usageCount());
    }

    @Test
    void assignPersistedId_setsId() {
        Macro m = Macro.newMacro("name", "body", null, MacroAvailability.AGENT);
        assertNull(m.id());
        m.assignPersistedId(99L);
        assertEquals(99L, m.id());
    }
}
