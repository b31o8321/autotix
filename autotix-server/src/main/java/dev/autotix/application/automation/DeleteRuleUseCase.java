package dev.autotix.application.automation;

import dev.autotix.domain.automation.AutomationRuleRepository;
import org.springframework.stereotype.Service;

/**
 * Deletes an automation rule by id.
 */
@Service
public class DeleteRuleUseCase {

    private final AutomationRuleRepository ruleRepository;

    public DeleteRuleUseCase(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public void delete(String ruleId) {
        ruleRepository.delete(ruleId);
    }
}
