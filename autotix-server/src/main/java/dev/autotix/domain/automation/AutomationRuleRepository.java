package dev.autotix.domain.automation;

import java.util.List;
import java.util.Optional;

/**
 * TODO: Repository port for AutomationRule.
 */
public interface AutomationRuleRepository {

    /** TODO: persist (insert or update) */
    String save(AutomationRule rule);

    Optional<AutomationRule> findById(String id);

    /** TODO: ordered by priority ascending, only enabled */
    List<AutomationRule> findAllEnabled();

    /** TODO: all (for admin listing) */
    List<AutomationRule> findAll();

    void delete(String id);
}
