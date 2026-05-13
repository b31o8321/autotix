package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Close a ticket locally AND remotely (via Plugin.close()).
 * Idempotent: closing an already-closed ticket is a no-op.
 *
 * If the remote platform close fails, we log a warning but still close locally —
 * the ticket state in Autotix must stay consistent.
 */
@Service
public class CloseTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(CloseTicketUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final PluginRegistry pluginRegistry;

    public CloseTicketUseCase(TicketRepository ticketRepository,
                              ChannelRepository channelRepository,
                              PluginRegistry pluginRegistry) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.pluginRegistry = pluginRegistry;
    }

    public void close(TicketId ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        // Idempotent: already closed, nothing to do
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

        ticket.close();
        ticketRepository.save(ticket);

        log.debug("Ticket closed: {}", ticketId.value());
    }
}
