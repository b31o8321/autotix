package dev.autotix.infrastructure.persistence.sla;

import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.TicketPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SlaPolicyRepositoryImpl using the H2 test profile.
 * Schema-h2.sql creates the sla_policy table; SlaPolicyBootstrap seeds defaults.
 */
@SpringBootTest
@ActiveProfiles("test")
class SlaPolicyRepositoryImplTest {

    @Autowired
    SlaPolicyRepository slaPolicyRepository;

    @Test
    void bootstrapSeedsDefaultPolicies() {
        // SlaPolicyBootstrap runs on startup — should have 4 rows
        List<SlaPolicy> all = slaPolicyRepository.findAll();
        assertEquals(4, all.size(), "Bootstrap should have seeded 4 default policies");
    }

    @Test
    void findByPriority_returnsCorrectPolicy() {
        Optional<SlaPolicy> high = slaPolicyRepository.findByPriority(TicketPriority.HIGH);
        assertTrue(high.isPresent());
        assertEquals(TicketPriority.HIGH, high.get().priority());
        assertEquals(60, high.get().firstResponseMinutes());
        assertEquals(480, high.get().resolutionMinutes());
    }

    @Test
    void save_update_roundTrip() {
        Optional<SlaPolicy> existing = slaPolicyRepository.findByPriority(TicketPriority.URGENT);
        assertTrue(existing.isPresent());
        SlaPolicy policy = existing.get();

        // Update
        policy.update("Updated Urgent SLA", 15, 120, true);
        slaPolicyRepository.save(policy);

        Optional<SlaPolicy> reloaded = slaPolicyRepository.findByPriority(TicketPriority.URGENT);
        assertTrue(reloaded.isPresent());
        assertEquals("Updated Urgent SLA", reloaded.get().name());
        assertEquals(15, reloaded.get().firstResponseMinutes());
        assertEquals(120, reloaded.get().resolutionMinutes());
    }

    @Test
    void findAllEnabled_returnsOnlyEnabled() {
        // All 4 defaults are enabled; verify
        List<SlaPolicy> enabled = slaPolicyRepository.findAllEnabled();
        assertTrue(enabled.size() >= 4, "At least 4 enabled policies expected after bootstrap");
    }
}
