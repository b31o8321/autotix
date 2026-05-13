package dev.autotix.application.automation;

import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRule.RuleAction;
import dev.autotix.domain.automation.AutomationRuleRepository;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.TicketEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates automation rules against a ticket event before AI dispatch.
 *
 * Rules are evaluated in priority order (ascending). The first matching rule's
 * actions are returned as a RuleOutcome. If no rule matches, a no-op outcome is returned.
 *
 * Facts map includes: subject, customerIdentifier, messageBody, channelType, platform.
 */
@Service
public class EvaluateRulesUseCase {

    private final AutomationRuleRepository ruleRepository;

    public EvaluateRulesUseCase(AutomationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public RuleOutcome evaluate(TicketEvent event, Channel channel) {
        List<AutomationRule> rules = ruleRepository.findAllEnabled();

        Map<String, Object> facts = buildFacts(event, channel);

        for (AutomationRule rule : rules) {
            if (rule.matches(facts)) {
                return fromActions(rule.actions());
            }
        }

        return RuleOutcome.noOp();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> buildFacts(TicketEvent event, Channel channel) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("subject", event.subject() != null ? event.subject() : "");
        facts.put("customerIdentifier", event.customerIdentifier() != null ? event.customerIdentifier() : "");
        facts.put("messageBody", event.messageBody() != null ? event.messageBody() : "");
        facts.put("channelType", channel.type() != null ? channel.type().name() : "");
        facts.put("platform", channel.platform() != null ? channel.platform().name() : "");
        return facts;
    }

    private RuleOutcome fromActions(List<RuleAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return RuleOutcome.noOp();
        }
        List<String> tags = new ArrayList<>();
        String assignee = null;
        boolean skipAi = false;
        AIAction finalAction = AIAction.NONE;

        for (RuleAction a : actions) {
            if (a.tags != null) {
                tags.addAll(a.tags);
            }
            if (a.assignee != null && !a.assignee.isEmpty()) {
                assignee = a.assignee;
            }
            if (a.skipAi) {
                skipAi = true;
            }
            if (a.action != null && a.action != AIAction.NONE) {
                finalAction = a.action;
            }
        }

        return new RuleOutcome(tags, assignee, skipAi, finalAction);
    }

    // -----------------------------------------------------------------------
    // Result type
    // -----------------------------------------------------------------------

    public static final class RuleOutcome {
        public final List<String> tags;
        public final String assignee;
        public final boolean skipAi;
        public final AIAction finalAction;

        public RuleOutcome(List<String> tags, String assignee, boolean skipAi, AIAction finalAction) {
            this.tags = tags != null ? tags : new ArrayList<>();
            this.assignee = assignee;
            this.skipAi = skipAi;
            this.finalAction = finalAction != null ? finalAction : AIAction.NONE;
        }

        public static RuleOutcome noOp() {
            return new RuleOutcome(new ArrayList<>(), null, false, AIAction.NONE);
        }

        public boolean isNoOp() {
            return tags.isEmpty() && assignee == null && !skipAi && finalAction == AIAction.NONE;
        }
    }
}
