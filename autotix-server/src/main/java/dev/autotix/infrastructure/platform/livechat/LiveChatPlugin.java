package dev.autotix.infrastructure.platform.livechat;

import com.alibaba.fastjson.JSON;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.livechat.LiveChatSessionRegistry;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LiveChat platform plugin.
 *
 * Transport: native WebSocket (handled by LiveChatWebSocketHandler).
 * parseWebhook is not used — inbound messages arrive over WS, not HTTP.
 * sendReply pushes an outbound frame to all open WS sessions for the ticket.
 * close pushes a status=CLOSED frame.
 */
@Component
public class LiveChatPlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(LiveChatPlugin.class);

    private final LiveChatSessionRegistry registry;

    public LiveChatPlugin(LiveChatSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.LIVECHAT;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.CHAT;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        throw new UnsupportedOperationException(
                "LiveChat uses WebSocket transport, not webhook HTTP. " +
                "Inbound messages are handled by LiveChatWebSocketHandler.");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // Determine author from the last OUTBOUND message on the ticket
        String author = resolveLastOutboundAuthor(ticket);

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "agent".equals(author) ? "agent_message" : "message");
        frame.put("author", author);
        frame.put("content", formattedReply);
        frame.put("occurredAt", java.time.Instant.now().toString());

        String json = JSON.toJSONString(frame);
        registry.pushToTicket(ticket.id(), json);
        log.debug("[LiveChat] pushed reply to ticket={} author={}", ticket.id().value(), author);
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        registry.pushStatus(ticket.id(), "CLOSED");
        log.debug("[LiveChat] pushed CLOSED status to ticket={}", ticket.id().value());
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        return true;
    }

    @Override
    public PlatformDescriptor descriptor() {
        String setupGuide =
                "Autotix Native LiveChat — embed on any website with a one-line script tag.\n" +
                "No external platform account required.\n\n" +
                "1. Create a LiveChat channel here and note the Webhook Token shown in the channel list.\n" +
                "2. Paste the embed snippet into the <head> or <body> of your website:\n" +
                "   <script src=\"http://your-autotix-host/widget/autotix-widget.js\"\n" +
                "           data-channel-token=\"YOUR_TOKEN\" async></script>\n" +
                "3. Open http://your-autotix-host/demo/livechat.html to test the widget.\n" +
                "4. Visitor messages appear in the Inbox automatically.";

        return new PlatformDescriptor(
                PlatformType.LIVECHAT,
                "LiveChat (native)",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.NONE,
                Collections.emptyList(),
                true,
                null,
                setupGuide
        );
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Reads the last OUTBOUND message on the ticket to detect whether it was sent
     * by "ai" or a human agent. Returns "ai" or "agent" accordingly.
     */
    private String resolveLastOutboundAuthor(Ticket ticket) {
        List<Message> messages = ticket.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.direction() == MessageDirection.OUTBOUND) {
                String a = m.author();
                if (a != null && a.startsWith("ai")) {
                    return "ai";
                }
                return "agent";
            }
        }
        return "agent";
    }
}
