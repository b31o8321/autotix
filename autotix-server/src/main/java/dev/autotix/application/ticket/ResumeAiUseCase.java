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
 * Use case: re-enable AI for a ticket that was previously escalated to human.
 *
 * Calls ticket.resumeAi, saves, and logs the AI_RESUMED activity.
 * Admin-only in the future; no authorization check here (enforced at controller layer).
 */
@Service
public class ResumeAiUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResumeAiUseCase.class);

    private final TicketRepository ticketRepository;
    private final TicketActivityRepository activityRepository;

    public ResumeAiUseCase(TicketRepository ticketRepository,
                            TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.activityRepository = activityRepository;
    }

    /**
     * @param ticketId the ticket to re-enable AI for
     * @param actorId  who triggered the resume (e.g. "agent:adminId")
     */
    public void resume(TicketId ticketId, String actorId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        ticket.resumeAi(actorId);
        ticketRepository.save(ticket);

        activityRepository.save(new TicketActivity(
                ticketId, actorId, TicketActivityAction.AI_RESUMED, Instant.now()));

        log.info("AI resumed for ticket {} by {}", ticketId.value(), actorId);
    }
}
