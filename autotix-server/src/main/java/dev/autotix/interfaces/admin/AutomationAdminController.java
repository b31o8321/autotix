package dev.autotix.interfaces.admin;

import dev.autotix.application.automation.CreateRuleUseCase;
import dev.autotix.application.automation.DeleteRuleUseCase;
import dev.autotix.application.automation.ListRulesUseCase;
import dev.autotix.application.automation.UpdateRuleUseCase;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.interfaces.admin.dto.AutomationRuleDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for automation rule CRUD.
 *
 * GET    /api/admin/automation/rules          — list all rules
 * POST   /api/admin/automation/rules          — create rule, returns saved DTO with id
 * PUT    /api/admin/automation/rules/{ruleId} — update rule
 * DELETE /api/admin/automation/rules/{ruleId} — delete rule
 */
@RestController
@RequestMapping("/api/admin/automation/rules")
@PreAuthorize("hasRole('ADMIN')")
public class AutomationAdminController {

    private final ListRulesUseCase listRulesUseCase;
    private final CreateRuleUseCase createRuleUseCase;
    private final UpdateRuleUseCase updateRuleUseCase;
    private final DeleteRuleUseCase deleteRuleUseCase;

    public AutomationAdminController(ListRulesUseCase listRulesUseCase,
                                     CreateRuleUseCase createRuleUseCase,
                                     UpdateRuleUseCase updateRuleUseCase,
                                     DeleteRuleUseCase deleteRuleUseCase) {
        this.listRulesUseCase = listRulesUseCase;
        this.createRuleUseCase = createRuleUseCase;
        this.updateRuleUseCase = updateRuleUseCase;
        this.deleteRuleUseCase = deleteRuleUseCase;
    }

    @GetMapping
    public List<AutomationRuleDTO> list() {
        return listRulesUseCase.listAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public AutomationRuleDTO create(@RequestBody AutomationRuleDTO dto) {
        AutomationRule rule = toDomain(dto);
        String id = createRuleUseCase.create(rule);
        dto.id = id;
        return dto;
    }

    @PutMapping("/{ruleId}")
    public void update(@PathVariable String ruleId, @RequestBody AutomationRuleDTO dto) {
        AutomationRule rule = toDomain(dto);
        updateRuleUseCase.update(ruleId, rule);
    }

    @DeleteMapping("/{ruleId}")
    public void delete(@PathVariable String ruleId) {
        deleteRuleUseCase.delete(ruleId);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private AutomationRuleDTO toDTO(AutomationRule rule) {
        AutomationRuleDTO dto = new AutomationRuleDTO();
        dto.id = rule.id();
        dto.name = rule.name();
        dto.priority = rule.priority();
        dto.enabled = rule.isEnabled();

        if (rule.conditions() != null) {
            dto.conditions = rule.conditions().stream().map(c -> {
                AutomationRuleDTO.ConditionDTO cd = new AutomationRuleDTO.ConditionDTO();
                cd.field = c.field;
                cd.op = c.op;
                cd.value = c.value;
                return cd;
            }).collect(Collectors.toList());
        }

        if (rule.actions() != null) {
            dto.actions = rule.actions().stream().map(a -> {
                AutomationRuleDTO.RuleActionDTO ad = new AutomationRuleDTO.RuleActionDTO();
                ad.action = a.action;
                ad.tags = a.tags;
                ad.assignee = a.assignee;
                ad.skipAi = a.skipAi;
                return ad;
            }).collect(Collectors.toList());
        }

        return dto;
    }

    private AutomationRule toDomain(AutomationRuleDTO dto) {
        List<AutomationRule.Condition> conditions = null;
        if (dto.conditions != null) {
            conditions = dto.conditions.stream().map(cd -> {
                AutomationRule.Condition c = new AutomationRule.Condition();
                c.field = cd.field;
                c.op = cd.op;
                c.value = cd.value;
                return c;
            }).collect(Collectors.toList());
        }

        List<AutomationRule.RuleAction> actions = null;
        if (dto.actions != null) {
            actions = dto.actions.stream().map(ad -> {
                AutomationRule.RuleAction a = new AutomationRule.RuleAction();
                a.action = ad.action != null ? ad.action : AIAction.NONE;
                a.tags = ad.tags;
                a.assignee = ad.assignee;
                a.skipAi = ad.skipAi;
                return a;
            }).collect(Collectors.toList());
        }

        return AutomationRule.create(
                dto.name,
                dto.priority,
                dto.enabled,
                conditions,
                actions);
    }
}
