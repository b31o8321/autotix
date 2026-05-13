package dev.autotix.infrastructure.platform.line;

import dev.autotix.domain.channel.*;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: LINE Messaging API integration (CHAT channel type).
 *  Auth: channel access token (long-lived) configured per bot.
 *  Webhook: X-Line-Signature.
 */
@Component
public class LinePlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.LINE; }
    @Override public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: parse LINE webhook event (events[].message)
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST /v2/bot/message/push
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: LINE has no native ticket close; local-only no-op
    }

    @Override public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET /v2/bot/info
        throw new UnsupportedOperationException("TODO");
    }
}
