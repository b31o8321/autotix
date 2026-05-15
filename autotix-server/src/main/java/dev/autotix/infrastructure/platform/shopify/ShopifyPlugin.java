package dev.autotix.infrastructure.platform.shopify;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
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

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.SHOPIFY,
                "Shopify",
                "ecommerce",
                ChannelType.EMAIL,
                Collections.singletonList(ChannelType.EMAIL),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("shop", "Shop Domain", "string", true)
                                .placeholder("acme.myshopify.com")
                                .help("Your Shopify store domain, e.g. acme.myshopify.com"),
                        PlatformDescriptor.AuthField.of("admin_api_token", "Admin API Token", "password", true)
                                .placeholder("shpat_…")
                                .help("The token starting with shpat_ from your custom app installation")
                ),
                false,
                "https://shopify.dev/docs/api/admin-rest",
                "1. In Shopify Admin → Settings → Apps and sales channels, click \"Develop apps\".\n" +
                "2. Click \"Create an app\", give it a name (e.g. Autotix).\n" +
                "3. Click \"Configure Admin API scopes\" and enable the scopes you need (e.g. read_orders, write_orders).\n" +
                "4. Click \"Install app\".\n" +
                "5. Under \"API credentials\", click \"Reveal token once\" and copy the Admin API access token (starts with shpat_).\n" +
                "Docs: https://shopify.dev/docs/api/admin-rest"
        );
    }
}
