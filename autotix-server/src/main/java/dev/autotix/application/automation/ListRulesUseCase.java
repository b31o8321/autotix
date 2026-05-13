package dev.autotix.application.automation;

import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lists all automation rules (for admin view).
 */
@Service
public class ListRulesUseCase {

    private final AutomationRuleRepository ruleRepository;

    public ListRulesUseCase(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public List<AutomationRule> listAll() {
        return ruleRepository.findAll();
    }
}
