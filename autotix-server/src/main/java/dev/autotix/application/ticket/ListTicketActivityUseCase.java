package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Return the activity log for a ticket, paginated (most recent first).
 */
@Service
public class ListTicketActivityUseCase {

    private final TicketActivityRepository activityRepository;

    public ListTicketActivityUseCase(TicketActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public List<TicketActivity> list(TicketId ticketId, int offset, int limit) {
        return activityRepository.findByTicketId(ticketId, offset, limit);
    }
}
