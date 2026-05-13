package dev.autotix.interfaces.admin.dto;

import dev.autotix.domain.ai.AIAction;

import java.util.List;

/**
 * REST DTO for AutomationRule admin CRUD.
 */
public class AutomationRuleDTO {

    public String id;
    public String name;
    public int priority;
    public boolean enabled;
    public List<ConditionDTO> conditions;
    public List<RuleActionDTO> actions;

    public static class ConditionDTO {
        public String field;
        public String op;
        public String value;
    }

    public static class RuleActionDTO {
        public AIAction action;
        public List<String> tags;
        public String assignee;
        public boolean skipAi;
    }
}
