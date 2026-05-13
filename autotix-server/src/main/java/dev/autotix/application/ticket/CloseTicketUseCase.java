package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketId;
import org.springframework.stereotype.Service;

/**
 * TODO: Close a ticket locally AND remotely (via Plugin.close()).
 *  Idempotent: closing an already-closed ticket is a no-op.
 */
@Service
public class CloseTicketUseCase {

    // TODO: inject TicketRepository, ChannelRepository, PluginRegistry
    public CloseTicketUseCase() {}

    public void close(TicketId ticketId) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
