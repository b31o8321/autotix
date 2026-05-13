package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketId;
import org.springframework.stereotype.Service;

/**
 * TODO: Triggered async after a webhook produced a ticket needing AI reply.
 *
 *  Flow:
 *    1. Acquire LockProvider lock on TicketId (prevent concurrent AI calls)
 *    2. Load Ticket
 *    3. Build AIRequest from Ticket's recent messages
 *    4. Call AIReplyPort.generate
 *    5. Pass result to ReplyTicketUseCase (which handles formatting + platform send)
 *    6. Apply optional AI action (close, tag, assign)
 *    7. Release lock
 *
 *  Failure handling: on AI error, mark ticket as escalated (ASSIGNED to fallback queue).
 */
@Service
public class DispatchAIReplyUseCase {

    // TODO: inject TicketRepository, AIReplyPort, ReplyTicketUseCase, LockProvider
    public DispatchAIReplyUseCase() {}

    public void dispatch(TicketId ticketId) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
