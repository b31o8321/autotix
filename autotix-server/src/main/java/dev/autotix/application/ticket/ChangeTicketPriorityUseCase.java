package dev.autotix.application.ticket;

import dev.autotix.application.sla.ApplySlaPolicyUseCase;
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
 * Also re-applies SLA deadlines based on the new priority (anchored at ticket.createdAt).
 */
@Service
public class ChangeTicketPriorityUseCase {

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;
    private final ApplySlaPolicyUseCase applySlaPolicyUseCase;

    public ChangeTicketPriorityUseCase(TicketRepository ticketRepository,
                                       TicketActivityRepository activityRepository,
                                       ApplySlaPolicyUseCase applySlaPolicyUseCase) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
        this.applySlaPolicyUseCase = applySlaPolicyUseCase;
    }

    public void change(TicketId ticketId, TicketPriority newPriority, String actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        TicketPriority oldPriority = ticket.priority();
        ticket.changePriority(newPriority);
        // Re-apply SLA deadlines anchored at createdAt; only due windows change, not the clock start
        applySlaPolicyUseCase.apply(ticket, Instant.now());
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
