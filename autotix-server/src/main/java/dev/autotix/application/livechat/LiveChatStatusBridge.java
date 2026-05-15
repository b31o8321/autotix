package dev.autotix.application.livechat;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.livechat.LiveChatSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Listens to InboxEvent.KIND = STATUS_CHANGED and pushes a status frame to any open
 * LiveChat WebSocket sessions for that ticket.
 *
 * Note: InboxEvent is a plain POJO, not a Spring ApplicationEvent, so this bridge
 * is wired directly from InboxEventPublisher.publish() rather than via @EventListener.
 * The bridge exposes a {@link #onInboxEvent(InboxEvent)} method that InboxEventPublisher
 * calls after its SSE fan-out.
 */
@Component
public class LiveChatStatusBridge {

    private static final Logger log = LoggerFactory.getLogger(LiveChatStatusBridge.class);

    private final TicketRepository ticketRepository;
    private final ChannelRepository channelRepository;
    private final LiveChatSessionRegistry registry;

    public LiveChatStatusBridge(TicketRepository ticketRepository,
                                ChannelRepository channelRepository,
                                LiveChatSessionRegistry registry) {
        this.ticketRepository = ticketRepository;
        this.channelRepository = channelRepository;
        this.registry = registry;
    }

    /**
     * Called by InboxEventPublisher whenever it publishes any event.
     * Filters for STATUS_CHANGED on LIVECHAT-platform channels only.
     */
    public void onInboxEvent(InboxEvent event) {
        if (event.kind != InboxEvent.Kind.STATUS_CHANGED) {
            return;
        }
        try {
            TicketId ticketId = new TicketId(event.ticketId);
            Optional<Ticket> ticketOpt = ticketRepository.findById(ticketId);
            if (!ticketOpt.isPresent()) {
                return;
            }
            Ticket ticket = ticketOpt.get();
            ChannelId channelId = ticket.channelId();
            Optional<Channel> channelOpt = channelRepository.findById(channelId);
            if (!channelOpt.isPresent()) {
                return;
            }
            if (channelOpt.get().platform() != PlatformType.LIVECHAT) {
                return;
            }
            String status = ticket.status().name();
            registry.pushStatus(ticketId, status);
            log.debug("[LiveChat] pushed status={} to ticket={}", status, ticketId.value());
        } catch (Exception e) {
            log.warn("[LiveChat] status bridge error for event {}: {}", event.ticketId, e.getMessage());
        }
    }
}
