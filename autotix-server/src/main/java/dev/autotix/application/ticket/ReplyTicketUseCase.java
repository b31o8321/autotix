package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.formatter.ReplyFormatter;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.platform.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Send a reply (from AI or human) back to the originating platform.
 *
 * Flow:
 *   1. Load Ticket + Channel
 *   2. Format reply via ReplyFormatter (markdown -> HTML/plain based on ChannelType)
 *   3. Resolve TicketPlatformPlugin from PluginRegistry
 *   4. Call plugin.sendReply(channel, ticket, formattedReply)
 *   5. Append outbound message (markdown) to Ticket; persist
 *   6. If author != "ai": publish AGENT_REPLIED event
 *      (AI path publishes AI_REPLIED in DispatchAIReplyUseCase to avoid double-publish)
 *
 * The outbound message stores the original markdown so AI history is consistent.
 */
@Service
public class ReplyTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReplyTicketUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final ReplyFormatter replyFormatter;
    private final PluginRegistry pluginRegistry;
    private final InboxEventPublisher inboxEventPublisher;

    public ReplyTicketUseCase(TicketRepository ticketRepository,
                              ChannelRepository channelRepository,
                              ReplyFormatter replyFormatter,
                              PluginRegistry pluginRegistry,
                              InboxEventPublisher inboxEventPublisher) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.replyFormatter = replyFormatter;
        this.pluginRegistry = pluginRegistry;
        this.inboxEventPublisher = inboxEventPublisher;
    }

    public void reply(TicketId ticketId, String markdownReply, String author) {
        // 1. Load ticket + channel
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        Channel channel = channelRepository.findById(ticket.channelId())
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + ticket.channelId().value()));

        // 2. Format reply per channel type
        String formattedReply = replyFormatter.format(channel.type(), markdownReply);

        // 3. Send via platform plugin (wrap non-domain exceptions)
        try {
            pluginRegistry.get(channel.platform()).sendReply(channel, ticket, formattedReply);
        } catch (AutotixException e) {
            throw e; // re-throw domain exceptions as-is
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    channel.platform().name(), "sendReply failed: " + e.getMessage(), e);
        }

        // 4. Append outbound message (store markdown, not formatted)
        ticket.appendOutbound(new Message(
                MessageDirection.OUTBOUND,
                author,
                markdownReply,
                Instant.now()));

        // 5. Persist
        ticketRepository.save(ticket);

        // 6. Publish AGENT_REPLIED only for human authors; AI path publishes AI_REPLIED separately
        if (!"ai".equals(author)) {
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.AGENT_REPLIED,
                    ticketId.value(),
                    channel.id().value(),
                    "Agent replied",
                    Instant.now()));
        }

        log.debug("Reply sent for ticket={} via platform={}", ticketId.value(), channel.platform());
    }
}
