package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Marks a ticket as SPAM (terminal status).
 * Logs MARKED_SPAM activity.
 */
@Service
public class MarkSpamUseCase {

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public MarkSpamUseCase(TicketRepository ticketRepository,
                            TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    public void mark(TicketId ticketId, String actorId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        Instant now = Instant.now();
        ticket.markSpam(now);
        ticketRepository.save(ticket);

        activityRepository.save(new TicketActivity(
                ticketId, actorId,
                TicketActivityAction.MARKED_SPAM,
                null,
                now));
    }
}
