package dev.autotix.infrastructure.platform.freshdesk;

import dev.autotix.domain.channel.*;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: Freshdesk integration (EMAIL channel type).
 *  Auth: API key (Basic auth: apikey:X).
 *  Webhook: configured via Freshdesk Observer with HMAC.
 */
@Component
public class FreshdeskPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.FRESHDESK; }
    @Override public ChannelType defaultChannelType() { return ChannelType.EMAIL; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: parse Freshdesk Observer payload
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST /api/v2/tickets/{id}/reply
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: PUT /api/v2/tickets/{id} status=5
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET /api/v2/agents/me
        throw new UnsupportedOperationException("TODO");
    }
}
