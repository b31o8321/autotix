package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Change the priority of a ticket and log a PRIORITY_CHANGED activity entry.
 */
@Service
public class ChangeTicketPriorityUseCase {

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public ChangeTicketPriorityUseCase(TicketRepository ticketRepository,
                                       TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    public void change(TicketId ticketId, TicketPriority newPriority, String actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        TicketPriority oldPriority = ticket.priority();
        ticket.changePriority(newPriority);
        ticketRepository.save(ticket);

        String details = "{\"from\":\"" + oldPriority.name() + "\",\"to\":\"" + newPriority.name() + "\"}";
        activityRepository.save(new TicketActivity(
                ticketId,
                actor,
                TicketActivityAction.PRIORITY_CHANGED,
                details,
                Instant.now()));
    }
}
