package dev.autotix.domain.automation;

import dev.autotix.domain.ai.AIAction;

import java.util.List;
import java.util.Map;

/**
 * TODO: User-defined rule evaluated before AI dispatch.
 *  Examples:
 *    - "if subject contains 'refund' -> assign to 'finance' team"
 *    - "if customer's email ends with @vip.com -> tag VIP, skip auto reply"
 *    - "if status==new and channel==zendesk -> auto reply on"
 *
 *  Rule shape: conditions (AND list) + actions list.
 *  Keep simple in v1; consider rule engine (Drools / spel) only if needed.
 */
public final class AutomationRule {

    private String id;
    private String name;
    private int priority;             // lower = higher priority
    private boolean enabled;
    private List<Condition> conditions;
    private List<RuleAction> actions;

    public String id() { return id; }
    public String name() { return name; }
    public int priority() { return priority; }
    public boolean isEnabled() { return enabled; }
    public List<Condition> conditions() { return conditions; }
    public List<RuleAction> actions() { return actions; }

    // TODO: evaluate against a TicketEvent — return true if all conditions match
    public boolean matches(Map<String, Object> facts) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: simple field-op-value condition (eq, contains, regex, in, ...). */
    public static final class Condition {
        public String field;
        public String op;
        public String value;
    }

    /** TODO: action to perform when rule matches. */
    public static final class RuleAction {
        public AIAction action;            // CLOSE / ASSIGN / TAG / NONE
        public List<String> tags;
        public String assignee;
        public boolean skipAi;
    }
}
