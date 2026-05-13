package dev.autotix.application.automation;

import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRuleRepository;
import org.springframework.stereotype.Service;

/**
 * Creates a new automation rule.
 */
@Service
public class CreateRuleUseCase {

    private final AutomationRuleRepository ruleRepository;

    public CreateRuleUseCase(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public String create(AutomationRule rule) {
        return ruleRepository.save(rule);
    }
}
