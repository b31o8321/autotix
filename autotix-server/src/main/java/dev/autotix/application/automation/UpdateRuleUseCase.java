package dev.autotix.application.automation;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRuleRepository;
import org.springframework.stereotype.Service;

/**
 * Updates an existing automation rule.
 */
@Service
public class UpdateRuleUseCase {

    private final AutomationRuleRepository ruleRepository;

    public UpdateRuleUseCase(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public void update(String ruleId, AutomationRule updated) {
        ruleRepository.findById(ruleId)
                .orElseThrow(() -> new AutotixException.NotFoundException("AutomationRule not found: " + ruleId));
        updated.setId(ruleId);
        ruleRepository.save(updated);
    }
}
