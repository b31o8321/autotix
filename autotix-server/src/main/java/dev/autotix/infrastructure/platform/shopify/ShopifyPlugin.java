package dev.autotix.infrastructure.platform.shopify;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Shopify order/customer integration.
 *
 * <p>Inbound: {@code orders/create}, {@code orders/cancelled}, {@code customers/create} webhooks
 * → converted to Autotix tickets.
 *
 * <p>Outbound reply: appends a timestamped note to the Shopify order via Admin API PUT.
 * For non-order tickets (e.g. customer signup), reply is skipped with a warning log.
 *
 * <p>Webhook signature: HMAC-SHA256 of raw body, base64-encoded, in {@code X-Shopify-Hmac-Sha256}.
 * Secret is stored in {@code channel.credential().attributes().get("webhook_shared_secret")}.
 * If the secret is absent, the webhook passes through with a warning log.
 *
 * <p>Auth: Admin API token in {@code X-Shopify-Access-Token} header.
 * Stored in {@code channel.credential().attributes().get("admin_api_token")}.
 */
@Component
public class ShopifyPlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(ShopifyPlugin.class);

    private final ShopifyClient shopifyClient;
    private final ShopifyWebhookParser webhookParser;

    @Autowired
    public ShopifyPlugin(ShopifyClient shopifyClient, ShopifyWebhookParser webhookParser) {
        this.shopifyClient = shopifyClient;
        this.webhookParser = webhookParser;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        String secret = null;
        if (channel.credential() != null && channel.credential().attributes() != null) {
            secret = channel.credential().attributes().get("webhook_shared_secret");
        }

        if (secret != null && !secret.isEmpty()) {
            boolean valid = webhookParser.verifySignature(headers, rawBody, secret);
            if (!valid) {
                throw new AutotixException.AuthException(
                        "Shopify webhook HMAC-SHA256 signature verification failed");
            }
        } else {
            log.warn("[SHOPIFY] No webhook_shared_secret configured for channel {}; " +
                     "accepting webhook without signature verification.", channel.id());
        }

        return webhookParser.parse(channel, headers, rawBody);
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        String orderId = ticket.externalNativeId();
        if (orderId == null || orderId.isEmpty()) {
            log.warn("[SHOPIFY] sendReply: ticket {} has no externalNativeId — skipping.", ticket.id());
            return;
        }
        // Guard: customer-signup tickets have no associated order.
        // We detect this heuristically: if the subject starts with "New customer signup"
        // we skip the reply rather than trying to PUT to an order endpoint with a customer ID.
        String subject = ticket.subject();
        if (subject != null && subject.startsWith("New customer signup")) {
            log.warn("[SHOPIFY] sendReply: ticket {} is a customer-signup ticket — " +
                     "no Shopify order to annotate; skipping.", ticket.id());
            return;
        }
        try {
            shopifyClient.appendOrderNote(channel.credential(), orderId, formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "shopify", "sendReply failed for ticket " + ticket.id(), e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // Shopify has no ticket/thread API — closing is a no-op on the platform side.
        log.debug("[SHOPIFY] close() called for ticket {} — no-op (Shopify has no thread to close).",
                ticket.id());
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        return shopifyClient.ping(credential);
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
                        PlatformDescriptor.AuthField.of("shop_domain", "Shop Domain", "string", true)
                                .placeholder("my-store.myshopify.com")
                                .help("Your Shopify store domain, e.g. my-store.myshopify.com"),
                        PlatformDescriptor.AuthField.of("admin_api_token", "Admin API Token", "password", true)
                                .placeholder("shpat_…")
                                .help("Admin API access token from your Shopify custom app (starts with shpat_)"),
                        PlatformDescriptor.AuthField.of("webhook_shared_secret", "Webhook Shared Secret", "password", false)
                                .placeholder("(optional — paste after creating the webhook)")
                                .help("Enables HMAC-SHA256 signature verification for incoming Shopify webhooks. " +
                                      "Set after creating the channel and configuring the Shopify webhook.")
                ),
                true,
                "https://shopify.dev/docs/api/admin-rest",
                "1. In Shopify Admin → Settings → Apps and sales channels → Develop apps → Create app.\n" +
                "2. Configure Admin API scopes: read_orders, write_orders, read_customers.\n" +
                "3. Install app; under API credentials click \"Reveal token once\" and copy the Admin API access token (starts with shpat_).\n" +
                "4. Settings → Notifications → Webhooks → Create webhook. Event = Order creation, Format = JSON, " +
                "URL = the inbound URL shown on this channel's Edit page after saving.\n" +
                "5. Copy the signing secret shown by Shopify and paste it on the Edit page to enable signature verification.\n" +
                "6. Repeat step 4-5 for Order cancellation and Customer creation events as needed.\n" +
                "Docs: https://shopify.dev/docs/api/admin-rest"
        );
    }
}
