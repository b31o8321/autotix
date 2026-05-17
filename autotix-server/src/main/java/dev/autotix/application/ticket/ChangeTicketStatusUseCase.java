package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Generic status change for a ticket.
 * Used by automation rules and bulk actions.
 * Does not handle SOLVE (use SolveTicketUseCase) or CLOSE (use CloseTicketUseCase).
 */
@Service
public class ChangeTicketStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(ChangeTicketStatusUseCase.class);

    private final TicketRepository ticketRepository;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    public ChangeTicketStatusUseCase(TicketRepository ticketRepository,
                                     InboxEventPublisher inboxEventPublisher,
                                     TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    public void change(TicketId ticketId, TicketStatus newStatus, String actor) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        TicketStatus oldStatus = ticket.status();
        if (oldStatus == newStatus) {
            return; // idempotent
        }

        String details = "{\"from\":\"" + oldStatus.name() + "\",\"to\":\"" + newStatus.name() + "\"}";

        ticket.changeStatus(newStatus);
        ticketRepository.save(ticket);

        Instant now = Instant.now();
        inboxEventPublisher.publish(new InboxEvent(
                InboxEvent.Kind.STATUS_CHANGED,
                ticketId.value(),
                ticket.channelId().value(),
                "status changed to " + newStatus,
                now));

        activityRepository.save(new TicketActivity(
                ticketId, actor,
                TicketActivityAction.STATUS_CHANGED,
                details,
                now));

        log.debug("Ticket {} status changed from {} to {} by {}", ticketId.value(), oldStatus, newStatus, actor);
    }
}
