package dev.autotix.application.ticket;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.TicketEvent;
import org.springframework.stereotype.Service;

/**
 * TODO: Entry point invoked by webhook controller after the platform plugin parses the
 *  raw payload into a standardized TicketEvent.
 *
 *  Flow:
 *    1. Idempotency check by (channelId, externalTicketId, occurredAt)
 *    2. Load or create Ticket
 *    3. Append inbound message
 *    4. Evaluate automation rules
 *    5. If autoReply enabled && rules don't skip -> enqueue DispatchAIReplyUseCase
 *    6. Persist Ticket
 */
@Service
public class ProcessWebhookUseCase {

    // TODO: inject TicketRepository, AutomationRuleRepository, QueueProvider, IdempotencyStore
    public ProcessWebhookUseCase() {}

    public void handle(Channel channel, TicketEvent event) {
        // TODO: implement orchestration described above
        throw new UnsupportedOperationException("TODO");
    }
}
