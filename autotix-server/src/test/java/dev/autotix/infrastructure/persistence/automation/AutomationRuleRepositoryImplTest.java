package dev.autotix.infrastructure.persistence.automation;

import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AutomationRuleRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class AutomationRuleRepositoryImplTest {

    @Autowired
    AutomationRuleRepository repository;

    private static AutomationRule.Condition cond(String field, String op, String value) {
        AutomationRule.Condition c = new AutomationRule.Condition();
        c.field = field;
        c.op = op;
        c.value = value;
        return c;
    }

    private static AutomationRule.RuleAction action(String assignee, boolean skipAi) {
        AutomationRule.RuleAction a = new AutomationRule.RuleAction();
        a.assignee = assignee;
        a.skipAi = skipAi;
        a.action = AIAction.NONE;
        return a;
    }

    @Test
    void saveAndFindById_roundTrip_preservesConditionsAndActions() {
        AutomationRule rule = AutomationRule.create(
                "round-trip-test-" + System.currentTimeMillis(),
                10,
                true,
                Collections.singletonList(cond("subject", "contains", "refund")),
                Collections.singletonList(action("billing", true)));

        String id = repository.save(rule);
        assertNotNull(id);

        Optional<AutomationRule> found = repository.findById(id);
        assertTrue(found.isPresent(), "Rule should be found by id");

        AutomationRule loaded = found.get();
        assertEquals("billing", loaded.actions().get(0).assignee);
        assertTrue(loaded.actions().get(0).skipAi);
        assertEquals(1, loaded.conditions().size());
        assertEquals("contains", loaded.conditions().get(0).op);
        assertEquals("refund", loaded.conditions().get(0).value);
    }

    @Test
    void findAllEnabled_excludesDisabledRules_andSortsByPriorityAsc() {
        String suffix = "-" + System.currentTimeMillis();

        AutomationRule enabled1 = AutomationRule.create("enabled-prio50" + suffix, 50, true,
                Collections.emptyList(), Collections.emptyList());
        AutomationRule enabled2 = AutomationRule.create("enabled-prio10" + suffix, 10, true,
                Collections.emptyList(), Collections.emptyList());
        AutomationRule disabled = AutomationRule.create("disabled-prio1" + suffix, 1, false,
                Collections.emptyList(), Collections.emptyList());

        String id1 = repository.save(enabled1);
        String id2 = repository.save(enabled2);
        String id3 = repository.save(disabled);

        List<AutomationRule> allEnabled = repository.findAllEnabled();

        // disabled rule must not appear
        boolean disabledPresent = allEnabled.stream()
                .anyMatch(r -> r.id().equals(id3));
        assertFalse(disabledPresent, "Disabled rule should not appear in findAllEnabled");

        // Both enabled rules must appear
        boolean id1Present = allEnabled.stream().anyMatch(r -> r.id().equals(id1));
        boolean id2Present = allEnabled.stream().anyMatch(r -> r.id().equals(id2));
        assertTrue(id1Present, "Enabled rule 1 should be present");
        assertTrue(id2Present, "Enabled rule 2 should be present");

        // Verify sorted by priority asc — among our inserted rules, prio10 before prio50
        int idx1 = -1, idx2 = -1;
        for (int i = 0; i < allEnabled.size(); i++) {
            if (allEnabled.get(i).id().equals(id1)) idx1 = i;
            if (allEnabled.get(i).id().equals(id2)) idx2 = i;
        }
        assertTrue(idx2 < idx1, "Rule with priority=10 should come before priority=50");
    }
}
