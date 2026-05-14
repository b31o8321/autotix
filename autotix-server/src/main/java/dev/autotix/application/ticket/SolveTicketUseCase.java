package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Solve a ticket — transitions status to SOLVED and stamps solvedAt.
 *
 * This is the primary "close" action for agents; the customer can still reopen
 * within the configured window by sending a new message.
 *
 * Idempotent: if ticket is already SOLVED, this is a no-op.
 *
 * Used by:
 *  - DeskController POST /api/desk/tickets/{id}/solve (primary agent action)
 *  - DispatchAIReplyUseCase when AI response.action() == CLOSE
 */
@Service
public class SolveTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(SolveTicketUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final InboxEventPublisher inboxEventPublisher;

    public SolveTicketUseCase(TicketRepository ticketRepository,
                              ChannelRepository channelRepository,
                              InboxEventPublisher inboxEventPublisher) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.inboxEventPublisher = inboxEventPublisher;
    }

    public void solve(TicketId ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        // Idempotent: already solved, nothing to do
        if (ticket.status() == TicketStatus.SOLVED) {
            return;
        }

        Channel channel = channelRepository.findById(ticket.channelId())
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + ticket.channelId().value()));

        Instant now = Instant.now();
        ticket.solve(now);
        ticketRepository.save(ticket);

        inboxEventPublisher.publish(new InboxEvent(
                InboxEvent.Kind.STATUS_CHANGED,
                ticketId.value(),
                channel.id().value(),
                "ticket solved",
                now));

        log.debug("Ticket solved: {}", ticketId.value());
    }
}
