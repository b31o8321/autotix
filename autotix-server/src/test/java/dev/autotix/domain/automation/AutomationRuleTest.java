package dev.autotix.domain.automation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for AutomationRule.matches() logic.
 */
class AutomationRuleTest {

    private static AutomationRule.Condition cond(String field, String op, String value) {
        AutomationRule.Condition c = new AutomationRule.Condition();
        c.field = field;
        c.op = op;
        c.value = value;
        return c;
    }

    private static Map<String, Object> facts(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length - 1; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void emptyConditions_matchesAlways() {
        AutomationRule rule = AutomationRule.create("empty", 1, true,
                Collections.emptyList(), Collections.emptyList());
        assertTrue(rule.matches(Collections.emptyMap()),
                "Empty conditions list should always return true");
        assertTrue(rule.matches(facts("subject", "hello")),
                "Empty conditions list should match any facts");
    }

    @Test
    void eqOp_matchAndMismatch() {
        AutomationRule rule = AutomationRule.create("eq-rule", 1, true,
                Collections.singletonList(cond("subject", "eq", "refund")),
                Collections.emptyList());

        assertTrue(rule.matches(facts("subject", "refund")), "eq should match exact value");
        assertFalse(rule.matches(facts("subject", "Refund")), "eq is case-sensitive");
        assertFalse(rule.matches(facts("subject", "refund request")), "eq must be exact");
    }

    @Test
    void containsOp_matchAndMismatch() {
        AutomationRule rule = AutomationRule.create("contains-rule", 1, true,
                Collections.singletonList(cond("messageBody", "contains", "urgent")),
                Collections.emptyList());

        assertTrue(rule.matches(facts("messageBody", "This is urgent please help")),
                "contains should match substring");
        assertFalse(rule.matches(facts("messageBody", "Everything is fine")),
                "contains should not match when substring absent");
    }

    @Test
    void regexOp_matchAndMismatch() {
        AutomationRule rule = AutomationRule.create("regex-rule", 1, true,
                Collections.singletonList(cond("customerIdentifier", "regex", ".*@vip\\.com")),
                Collections.emptyList());

        assertTrue(rule.matches(facts("customerIdentifier", "alice@vip.com")),
                "regex should match vip email");
        assertFalse(rule.matches(facts("customerIdentifier", "alice@normal.com")),
                "regex should not match non-vip email");
    }

    @Test
    void inOp_matchAndMismatch() {
        AutomationRule rule = AutomationRule.create("in-rule", 1, true,
                Collections.singletonList(cond("platform", "in", "ZENDESK,FRESHDESK")),
                Collections.emptyList());

        assertTrue(rule.matches(facts("platform", "ZENDESK")),
                "in should match when value is in comma-separated list");
        assertTrue(rule.matches(facts("platform", "FRESHDESK")),
                "in should match second item in list");
        assertFalse(rule.matches(facts("platform", "SHOPIFY")),
                "in should not match when value not in list");
    }

    @Test
    void multipleConditions_allMustMatch() {
        AutomationRule rule = AutomationRule.create("multi", 1, true,
                Arrays.asList(
                        cond("subject", "contains", "refund"),
                        cond("platform", "eq", "ZENDESK")),
                Collections.emptyList());

        assertTrue(rule.matches(facts("subject", "refund request", "platform", "ZENDESK")),
                "All conditions met should match");
        assertFalse(rule.matches(facts("subject", "refund request", "platform", "FRESHDESK")),
                "If one condition fails, should not match");
    }

    @Test
    void missingFactField_doesNotMatch() {
        AutomationRule rule = AutomationRule.create("missing-field", 1, true,
                Collections.singletonList(cond("subject", "eq", "hello")),
                Collections.emptyList());

        assertFalse(rule.matches(Collections.emptyMap()),
                "Missing fact field should not match");
        assertFalse(rule.matches(facts("platform", "ZENDESK")),
                "Missing specific fact field should not match");
    }
}
