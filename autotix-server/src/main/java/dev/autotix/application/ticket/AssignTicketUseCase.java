package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

/**
 * Assign ticket to a human agent (local-only).
 */
@Service
public class AssignTicketUseCase {

    private final TicketRepository ticketRepository;

    public AssignTicketUseCase(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public void assign(TicketId ticketId, String agentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));
        ticket.assignTo(agentId);
        ticketRepository.save(ticket);
    }
}
