package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.MessageVisibility;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
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
 * Send a reply (from AI or human) back to the originating platform, or save an
 * internal note without sending externally.
 *
 * PUBLIC reply flow:
 *   1. Load Ticket + Channel
 *   2. Format reply via ReplyFormatter
 *   3. Call plugin.sendReply(...)
 *   4. Append OUTBOUND/PUBLIC message; persist
 *   5. Publish AGENT_REPLIED (for human authors)
 *   6. Log REPLIED_PUBLIC activity
 *
 * INTERNAL note flow:
 *   1. Load Ticket
 *   2. Append OUTBOUND/INTERNAL message via appendInternalNote(); persist
 *      (no external send; status NOT changed)
 *   3. Publish AGENT_REPLIED (still notifies inbox)
 *   4. Log REPLIED_INTERNAL activity
 */
@Service
public class ReplyTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReplyTicketUseCase.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final ReplyFormatter replyFormatter;
    private final PluginRegistry pluginRegistry;
    private final InboxEventPublisher inboxEventPublisher;
    private final TicketActivityRepository activityRepository;

    public ReplyTicketUseCase(TicketRepository ticketRepository,
                              ChannelRepository channelRepository,
                              ReplyFormatter replyFormatter,
                              PluginRegistry pluginRegistry,
                              InboxEventPublisher inboxEventPublisher,
                              TicketActivityRepository activityRepository) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.replyFormatter = replyFormatter;
        this.pluginRegistry = pluginRegistry;
        this.inboxEventPublisher = inboxEventPublisher;
        this.activityRepository = activityRepository;
    }

    /**
     * Backward-compatible overload — sends a PUBLIC reply.
     */
    public void reply(TicketId ticketId, String markdownReply, String author) {
        reply(ticketId, markdownReply, author, false);
    }

    /**
     * Send a reply or save an internal note.
     *
     * @param internal true → INTERNAL note (no external send, no status change)
     */
    public void reply(TicketId ticketId, String markdownReply, String author, boolean internal) {
        // 1. Load ticket
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Ticket not found: " + ticketId.value()));

        Instant now = Instant.now();

        if (internal) {
            // Internal note path — no plugin call, no status change
            ticket.appendInternalNote(new Message(
                    MessageDirection.OUTBOUND,
                    author,
                    markdownReply,
                    now,
                    MessageVisibility.INTERNAL));

            ticketRepository.save(ticket);

            // Still publish so inbox updates in real time
            if (!"ai".equals(author)) {
                Channel channel = channelRepository.findById(ticket.channelId())
                        .orElse(null);
                String channelIdVal = channel != null ? channel.id().value() : ticket.channelId().value();
                inboxEventPublisher.publish(new InboxEvent(
                        InboxEvent.Kind.AGENT_REPLIED,
                        ticketId.value(),
                        channelIdVal,
                        "Internal note added",
                        now));
            }

            activityRepository.save(new TicketActivity(
                    ticketId, author,
                    TicketActivityAction.REPLIED_INTERNAL,
                    now));

            log.debug("Internal note saved for ticket={}", ticketId.value());
            return;
        }

        // Public reply path
        Channel channel = channelRepository.findById(ticket.channelId())
                .orElseThrow(() -> new AutotixException.NotFoundException(
                        "Channel not found: " + ticket.channelId().value()));

        // 2. Format reply per channel type
        String formattedReply = replyFormatter.format(channel.type(), markdownReply);

        // 3. Send via platform plugin
        try {
            pluginRegistry.get(channel.platform()).sendReply(channel, ticket, formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    channel.platform().name(), "sendReply failed: " + e.getMessage(), e);
        }

        // 4. Append outbound message (store markdown, not formatted)
        ticket.appendOutbound(new Message(
                MessageDirection.OUTBOUND,
                author,
                markdownReply,
                now,
                MessageVisibility.PUBLIC));

        // 5. Persist
        ticketRepository.save(ticket);

        // 6. Publish AGENT_REPLIED only for human authors
        if (!"ai".equals(author)) {
            inboxEventPublisher.publish(new InboxEvent(
                    InboxEvent.Kind.AGENT_REPLIED,
                    ticketId.value(),
                    channel.id().value(),
                    "Agent replied",
                    now));
        }

        // 7. Log activity
        activityRepository.save(new TicketActivity(
                ticketId, author,
                TicketActivityAction.REPLIED_PUBLIC,
                now));

        log.debug("Reply sent for ticket={} via platform={}", ticketId.value(), channel.platform());
    }
}
