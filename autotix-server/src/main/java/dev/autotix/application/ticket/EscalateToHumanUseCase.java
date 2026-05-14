package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Use case: escalate a ticket to a human agent, suspending AI replies.
 *
 * Calls ticket.escalateToHuman, saves, and logs the ESCALATED activity.
 */
@Service
public class EscalateToHumanUseCase {

    private static final Logger log = LoggerFactory.getLogger(EscalateToHumanUseCase.class);

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public EscalateToHumanUseCase(TicketRepository ticketRepository,
                                   TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * @param ticketId the ticket to escalate
     * @param actorId  who triggered the escalation (e.g. "agent:userId")
     * @param reason   optional free-text reason
     */
    public void escalate(TicketId ticketId, String actorId, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        ticket.escalateToHuman(actorId, reason);
        ticketRepository.save(ticket);

        String details = reason != null
                ? "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}"
                : null;
        activityRepository.save(new TicketActivity(
                ticketId, actorId, TicketActivityAction.ESCALATED, details, Instant.now()));

        log.info("Ticket {} escalated to human by {} — reason: {}", ticketId.value(), actorId, reason);
    }
}
