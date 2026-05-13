package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketId;
import org.springframework.stereotype.Service;

/**
 * TODO: Send a reply (from AI or human) back to the originating platform.
 *
 *  Flow:
 *    1. Load Ticket + Channel
 *    2. Format reply via ReplyFormatter (markdown -&gt; HTML/plain based on ChannelType)
 *    3. Resolve TicketPlatformPlugin from PluginRegistry
 *    4. Call plugin.sendReply(channel, ticket, formattedReply)
 *    5. Append outbound message to Ticket; persist
 */
@Service
public class ReplyTicketUseCase {

    // TODO: inject TicketRepository, ChannelRepository, ReplyFormatter, PluginRegistry
    public ReplyTicketUseCase() {}

    public void reply(TicketId ticketId, String markdownReply, String author) {
        // TODO: implement
        throw new UnsupportedOperationException("TODO");
    }
}
