package dev.autotix.application.automation;

import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.automation.AutomationRule;
import dev.autotix.domain.automation.AutomationRuleRepository;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for EvaluateRulesUseCase.
 */
@ExtendWith(MockitoExtension.class)
class EvaluateRulesUseCaseTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    private EvaluateRulesUseCase useCase;

    private TicketEvent sampleEvent;
    private Channel sampleChannel;

    @BeforeEach
    void setUp() {
        useCase = new EvaluateRulesUseCase(ruleRepository);

        sampleEvent = new TicketEvent(
                new ChannelId("ch-1"),
                EventType.NEW_TICKET,
                "ext-1",
                "customer@example.com",
                "Alice",
                "Refund request",
                "I need a refund",
                Instant.now(),
                Collections.emptyMap());

        sampleChannel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Channel",
                "tok",
                null,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    @Test
    void noRules_returnsNoOpOutcome() {
        when(ruleRepository.findAllEnabled()).thenReturn(Collections.emptyList());

        EvaluateRulesUseCase.RuleOutcome outcome = useCase.evaluate(sampleEvent, sampleChannel);

        assertNotNull(outcome);
        assertTrue(outcome.isNoOp(), "No rules -> outcome should be no-op");
        assertFalse(outcome.skipAi);
        assertNull(outcome.assignee);
        assertTrue(outcome.tags.isEmpty());
        assertEquals(AIAction.NONE, outcome.finalAction);
    }

    @Test
    void matchingRule_returnsItsActions() {
        AutomationRule.Condition cond = new AutomationRule.Condition();
        cond.field = "subject";
        cond.op = "contains";
        cond.value = "Refund";

        AutomationRule.RuleAction action = new AutomationRule.RuleAction();
        action.assignee = "billing-team";
        action.skipAi = true;
        action.action = AIAction.NONE;
        action.tags = Collections.singletonList("refund");

        AutomationRule rule = AutomationRule.create("refund-rule", 10, true,
                Collections.singletonList(cond),
                Collections.singletonList(action));

        when(ruleRepository.findAllEnabled()).thenReturn(Collections.singletonList(rule));

        EvaluateRulesUseCase.RuleOutcome outcome = useCase.evaluate(sampleEvent, sampleChannel);

        assertEquals("billing-team", outcome.assignee);
        assertTrue(outcome.skipAi);
        assertTrue(outcome.tags.contains("refund"));
    }

    @Test
    void higherPriorityRuleWins() {
        // priority 5 rule matches (returns assignee=high-priority-team)
        AutomationRule.Condition cond1 = new AutomationRule.Condition();
        cond1.field = "subject";
        cond1.op = "contains";
        cond1.value = "Refund";

        AutomationRule.RuleAction action1 = new AutomationRule.RuleAction();
        action1.assignee = "high-priority-team";
        action1.action = AIAction.NONE;

        AutomationRule highPriority = AutomationRule.create("high", 5, true,
                Collections.singletonList(cond1),
                Collections.singletonList(action1));

        // priority 50 rule also matches (returns assignee=low-priority-team)
        AutomationRule.Condition cond2 = new AutomationRule.Condition();
        cond2.field = "subject";
        cond2.op = "contains";
        cond2.value = "Refund";

        AutomationRule.RuleAction action2 = new AutomationRule.RuleAction();
        action2.assignee = "low-priority-team";
        action2.action = AIAction.NONE;

        AutomationRule lowPriority = AutomationRule.create("low", 50, true,
                Collections.singletonList(cond2),
                Collections.singletonList(action2));

        // findAllEnabled returns sorted by priority asc (lower number = higher priority)
        when(ruleRepository.findAllEnabled()).thenReturn(Arrays.asList(highPriority, lowPriority));

        EvaluateRulesUseCase.RuleOutcome outcome = useCase.evaluate(sampleEvent, sampleChannel);

        assertEquals("high-priority-team", outcome.assignee,
                "First matching rule (priority=5) should win over priority=50");
    }

    @Test
    void nonMatchingRule_returnsNoOp() {
        AutomationRule.Condition cond = new AutomationRule.Condition();
        cond.field = "subject";
        cond.op = "eq";
        cond.value = "this-will-not-match";

        AutomationRule rule = AutomationRule.create("no-match-rule", 1, true,
                Collections.singletonList(cond),
                Collections.emptyList());

        when(ruleRepository.findAllEnabled()).thenReturn(Collections.singletonList(rule));

        EvaluateRulesUseCase.RuleOutcome outcome = useCase.evaluate(sampleEvent, sampleChannel);

        assertTrue(outcome.isNoOp(), "No matching rule -> outcome should be no-op");
    }
}
