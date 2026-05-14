package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Permanently close a ticket — transitions to CLOSED (terminal state).
 *
 * After permanent close, any new inbound message from the customer on the same
 * externalNativeId will spawn a new ticket (via ProcessWebhookUseCase).
 *
 * Idempotent: closing an already-closed ticket is a no-op.
 *
 * For the normal agent "resolve" action use {@link SolveTicketUseCase} instead.
 * This use-case is for admin-only permanent termination or spam escalation.
 *
 * Endpoint: POST /api/desk/tickets/{id}/close  (admin/permanent action)
 */
@Service
public class CloseTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseTicketUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final PluginRegistry pluginRegistry;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    public CloseTicketUseCase(TicketRepository ticketRepository,
                              ChannelRepository channelRepository,
                              PluginRegistry pluginRegistry,
                              InboxEventPublisher inboxEventPublisher,
                              TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.pluginRegistry = pluginRegistry;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    /**
     * Permanently close the ticket locally AND remotely (via Plugin.close()).
     * Idempotent: closing an already-closed ticket is a no-op.
     */
    public void close(TicketId ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        // Idempotent: already permanently closed, nothing to do
        if (ticket.status() == TicketStatus.CLOSED) {
            return;
        }

        Channel channel = channelRepository.findById(ticket.channelId())
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + ticket.channelId().value()));

        // Attempt remote close — log warning on failure but proceed locally
        try {
            pluginRegistry.get(channel.platform()).close(channel, ticket);
        } catch (AutotixException.IntegrationException e) {
            log.warn("Remote close failed for ticket={} on platform={}: {}",
                    ticketId.value(), channel.platform(), e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error during remote close for ticket={}: {}",
                    ticketId.value(), e.getMessage());
        }

        Instant now = Instant.now();
        ticket.permanentClose(now);
        ticketRepository.save(ticket);

        inboxEventPublisher.publish(new InboxEvent(
                InboxEvent.Kind.STATUS_CHANGED,
                ticketId.value(),
                channel.id().value(),
                "ticket permanently closed",
                now));

        activityRepository.save(new TicketActivity(
                ticketId, "system",
                TicketActivityAction.PERMANENTLY_CLOSED,
                now));

        log.debug("Ticket permanently closed: {}", ticketId.value());
    }
}
