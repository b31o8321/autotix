package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Paginated list for desk UI.
 */
@Service
public class ListTicketsUseCase {

    private final TicketRepository ticketRepository;

    public ListTicketsUseCase(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public List<Ticket> list(TicketSearchQuery query) {
        return ticketRepository.search(query);
    }
}
