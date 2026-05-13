package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketSearchQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TODO: Paginated list for desk UI.
 */
@Service
public class ListTicketsUseCase {

    public ListTicketsUseCase() {}

    public List<Ticket> list(TicketSearchQuery query) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
