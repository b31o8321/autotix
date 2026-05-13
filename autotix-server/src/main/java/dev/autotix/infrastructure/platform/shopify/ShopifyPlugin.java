package dev.autotix.infrastructure.platform.shopify;

import dev.autotix.domain.channel.*;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: Shopify Inbox (Email + Chat product). v1 covers buyer-message email flow.
 *  Auth: OAuth via Shopify App Bridge.
 *  Webhook: X-Shopify-Hmac-Sha256.
 */
@Component
public class ShopifyPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.SHOPIFY; }
    @Override public ChannelType defaultChannelType() { return ChannelType.EMAIL; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: verify HMAC; parse buyer message
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: GraphQL Admin API — send reply through Shopify Inbox
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: mark conversation resolved
        throw new UnsupportedOperationException("TODO");
    }

    @Override public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET /admin/api/2025-01/shop.json
        throw new UnsupportedOperationException("TODO");
    }
}
