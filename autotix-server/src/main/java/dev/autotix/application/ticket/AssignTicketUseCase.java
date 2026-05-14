package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Assign ticket to a human agent (local-only).
 */
@Service
public class AssignTicketUseCase {

    private final TicketRepository ticketRepository;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    public AssignTicketUseCase(TicketRepository ticketRepository,
                               InboxEventPublisher inboxEventPublisher,
                               TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    public void assign(TicketId ticketId, String agentId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));
        ticket.assignTo(agentId);
        ticketRepository.save(ticket);

        Instant now = Instant.now();
        // Publish ASSIGNED event
        inboxEventPublisher.publish(new InboxEvent(
                InboxEvent.Kind.ASSIGNED,
                ticketId.value(),
                ticket.channelId().value(),
                "assigned to " + agentId,
                now));

        activityRepository.save(new TicketActivity(
                ticketId, "system",
                TicketActivityAction.ASSIGNED,
                "{\"assignee\":\"" + agentId + "\"}",
                now));
    }
}
