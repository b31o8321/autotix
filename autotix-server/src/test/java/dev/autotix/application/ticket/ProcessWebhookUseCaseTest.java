package dev.autotix.application.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TODO: UseCase test with mocked ports (Mockito).
 *
 *  Suggested cases:
 *    - Idempotency: second call with same (channelId, externalTicketId, occurredAt) is a no-op
 *    - First-time event creates new Ticket and persists
 *    - When channel.autoReplyEnabled, queue dispatches AI reply job
 *    - When automation rule has skipAi=true, no AI dispatch is enqueued
 *    - Rule with action=ASSIGN routes ticket to assignee without AI
 */
@ExtendWith(MockitoExtension.class)
class ProcessWebhookUseCaseTest {

    @Test
    void idempotentReplay_isNoOp() {
        // TODO
    }

    @Test
    void firstTime_createsTicket_andQueuesAi() {
        // TODO
    }

    @Test
    void rule_withSkipAi_doesNotQueue() {
        // TODO
    }
}
