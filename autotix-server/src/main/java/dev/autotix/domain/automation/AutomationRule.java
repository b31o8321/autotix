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

    // Setters for persistence layer rehydration
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }
    public void setActions(List<RuleAction> actions) { this.actions = actions; }

    /** Factory for creating a new rule (id is null until persisted). */
    public static AutomationRule create(String name, int priority, boolean enabled,
                                        List<Condition> conditions, List<RuleAction> actions) {
        AutomationRule r = new AutomationRule();
        r.name = name;
        r.priority = priority;
        r.enabled = enabled;
        r.conditions = conditions;
        r.actions = actions;
        return r;
    }

    /**
     * Evaluate all conditions (AND logic) against the given facts map.
     * Returns true only if every condition matches.
     * Supported ops: eq, neq, contains (substring), in (comma-separated list), regex (full match).
     * A null fact value for the referenced field is treated as no-match.
     */
    public boolean matches(Map<String, Object> facts) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (Condition c : conditions) {
            Object factVal = facts.get(c.field);
            if (factVal == null) {
                return false;
            }
            String factStr = factVal.toString();
            boolean conditionMet;
            switch (c.op) {
                case "eq":
                    conditionMet = factStr.equals(c.value);
                    break;
                case "neq":
                    conditionMet = !factStr.equals(c.value);
                    break;
                case "contains":
                    conditionMet = factStr.contains(c.value);
                    break;
                case "in": {
                    conditionMet = false;
                    if (c.value != null) {
                        for (String item : c.value.split(",")) {
                            if (factStr.equals(item.trim())) {
                                conditionMet = true;
                                break;
                            }
                        }
                    }
                    break;
                }
                case "regex":
                    conditionMet = factStr.matches(c.value);
                    break;
                default:
                    conditionMet = false;
                    break;
            }
            if (!conditionMet) {
                return false;
            }
        }
        return true;
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
