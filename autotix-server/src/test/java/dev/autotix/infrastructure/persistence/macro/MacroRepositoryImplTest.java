package dev.autotix.infrastructure.persistence.macro;

import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.domain.macro.MacroRepository;
import dev.autotix.domain.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MacroRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class MacroRepositoryImplTest {

    @Autowired
    private MacroRepository macroRepository;

    private String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void save_and_findById_roundTrip() {
        Macro m = Macro.newMacro(unique("rt"), "Hello world", "billing", MacroAvailability.AGENT);
        Long id = macroRepository.save(m);
        assertNotNull(id);

        Optional<Macro> found = macroRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Hello world", found.get().bodyMarkdown());
        assertEquals("billing", found.get().category());
        assertEquals(MacroAvailability.AGENT, found.get().availableTo());
    }

    @Test
    void findAll_orderedByUsageCountDescThenNameAsc() {
        String ts = String.valueOf(System.nanoTime());
        Macro low = Macro.newMacro("aaa-low-" + ts, "body", null, MacroAvailability.AGENT);
        Macro high = Macro.newMacro("zzz-high-" + ts, "body", null, MacroAvailability.AGENT);
        macroRepository.save(low);
        macroRepository.save(high);

        // Bump high macro usage
        high.recordUsage();
        high.recordUsage();
        macroRepository.save(high);

        List<Macro> all = macroRepository.findAll();
        // Find our test macros
        int highIdx = -1, lowIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(high.id())) highIdx = i;
            if (all.get(i).id().equals(low.id())) lowIdx = i;
        }
        assertTrue(highIdx >= 0, "high macro should be in list");
        assertTrue(lowIdx >= 0, "low macro should be in list");
        assertTrue(highIdx < lowIdx, "high usage macro should appear before low usage macro");
    }

    @Test
    void findVisibleTo_agent_excludesAdminOnly() {
        String ts = String.valueOf(System.nanoTime());
        Macro adminOnly = Macro.newMacro("admin-only-" + ts, "body", null, MacroAvailability.ADMIN_ONLY);
        Macro agentVisible = Macro.newMacro("agent-vis-" + ts, "body", null, MacroAvailability.AGENT);
        Macro aiVisible = Macro.newMacro("ai-vis-" + ts, "body", null, MacroAvailability.AI);
        macroRepository.save(adminOnly);
        macroRepository.save(agentVisible);
        macroRepository.save(aiVisible);

        List<Macro> agentView = macroRepository.findVisibleTo(UserRole.AGENT);
        boolean seesAdminOnly = agentView.stream().anyMatch(m -> m.id().equals(adminOnly.id()));
        boolean seesAgent = agentView.stream().anyMatch(m -> m.id().equals(agentVisible.id()));
        boolean seesAi = agentView.stream().anyMatch(m -> m.id().equals(aiVisible.id()));

        assertFalse(seesAdminOnly, "Agent should NOT see ADMIN_ONLY macros");
        assertTrue(seesAgent, "Agent should see AGENT macros");
        assertTrue(seesAi, "Agent should see AI macros");
    }

    @Test
    void findVisibleTo_admin_seesAll() {
        String ts = String.valueOf(System.nanoTime());
        Macro adminOnly = Macro.newMacro("adm-all-" + ts, "body", null, MacroAvailability.ADMIN_ONLY);
        macroRepository.save(adminOnly);

        List<Macro> adminView = macroRepository.findVisibleTo(UserRole.ADMIN);
        boolean seen = adminView.stream().anyMatch(m -> m.id().equals(adminOnly.id()));
        assertTrue(seen, "Admin should see ADMIN_ONLY macros");
    }

    @Test
    void duplicateName_causesConstraintViolation() {
        String name = unique("dupe");
        Macro first = Macro.newMacro(name, "body", null, MacroAvailability.AGENT);
        macroRepository.save(first);

        Macro second = Macro.newMacro(name, "other body", null, MacroAvailability.AGENT);
        assertThrows(Exception.class, () -> macroRepository.save(second),
                "Duplicate name should throw a DB constraint exception");
    }

    @Test
    void delete_removesRecord() {
        Macro m = Macro.newMacro(unique("del"), "body", null, MacroAvailability.AGENT);
        macroRepository.save(m);
        Long id = m.id();
        macroRepository.delete(id);
        assertFalse(macroRepository.findById(id).isPresent(), "Deleted macro should not be found");
    }
}
