package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketId;
import org.springframework.stereotype.Service;

/**
 * TODO: Assign ticket to a human agent.
 *  Local-only by default; some platforms support remote assign — left to Plugin
 *  capability flag, not required.
 */
@Service
public class AssignTicketUseCase {

    public AssignTicketUseCase() {}

    public void assign(TicketId ticketId, String agentId) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
