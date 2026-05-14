package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketType;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Change the type of a ticket and log a TYPE_CHANGED activity entry.
 */
@Service
public class ChangeTicketTypeUseCase {

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public ChangeTicketTypeUseCase(TicketRepository ticketRepository,
                                   TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    public void change(TicketId ticketId, TicketType newType, String actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        TicketType oldType = ticket.type();
        ticket.changeType(newType);
        ticketRepository.save(ticket);

        String details = "{\"from\":\"" + oldType.name() + "\",\"to\":\"" + newType.name() + "\"}";
        activityRepository.save(new TicketActivity(
                ticketId,
                actor,
                TicketActivityAction.TYPE_CHANGED,
                details,
                Instant.now()));
    }
}
