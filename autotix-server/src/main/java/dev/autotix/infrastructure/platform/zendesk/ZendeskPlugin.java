package dev.autotix.infrastructure.platform.zendesk;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Zendesk Tickets integration (EMAIL channel type).
 *
 * <p>Webhook signature: verified via HMAC-SHA256.
 * Webhook secret is stored in {@code channel.credential().attributes().get("webhook_secret")}.
 *
 * <p>OAuth: startOAuth / exchangeCode are deferred to v2 (see {@link ZendeskClient#oauthExchange}).
 */
@Component
public class ZendeskPlugin implements TicketPlatformPlugin {

    private final ZendeskClient zendeskClient;
    private final ZendeskWebhookParser webhookParser;

    @Autowired
    public ZendeskPlugin(ZendeskClient zendeskClient, ZendeskWebhookParser webhookParser) {
        this.zendeskClient = zendeskClient;
        this.webhookParser = webhookParser;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.ZENDESK;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        String webhookSecret = channel.credential() != null && channel.credential().attributes() != null
                ? channel.credential().attributes().get("webhook_secret")
                : null;

        if (webhookSecret != null && !webhookSecret.isEmpty()) {
            boolean valid = webhookParser.verifySignature(headers, rawBody, webhookSecret);
            if (!valid) {
                throw new AutotixException.AuthException(
                        "Zendesk webhook signature verification failed");
            }
        }

        return webhookParser.parse(channel, headers, rawBody);
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        try {
            zendeskClient.postComment(
                    channel.credential(),
                    ticket.externalNativeId(),
                    formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "zendesk", "sendReply failed for ticket " + ticket.externalNativeId(), e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        try {
            zendeskClient.updateStatus(
                    channel.credential(),
                    ticket.externalNativeId(),
                    "solved");
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "zendesk", "close failed for ticket " + ticket.externalNativeId(), e);
        }
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        return zendeskClient.ping(credential);
    }

    @Override
    public String startOAuth(String state) {
        throw new UnsupportedOperationException("Zendesk OAuth implemented in v2");
    }

    @Override
    public ChannelCredential exchangeCode(String code) {
        throw new UnsupportedOperationException("Zendesk OAuth implemented in v2");
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.ZENDESK,
                "Zendesk",
                "ticket",
                ChannelType.EMAIL,
                Collections.singletonList(ChannelType.EMAIL),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("subdomain", "Zendesk Subdomain", "string", true)
                                .placeholder("yourcompany")
                                .help("The subdomain part of your Zendesk URL: yourcompany.zendesk.com"),
                        PlatformDescriptor.AuthField.of("email", "Agent Email", "string", true)
                                .placeholder("you@yourcompany.com")
                                .help("The email of the Zendesk agent account used for API calls"),
                        PlatformDescriptor.AuthField.of("api_token", "API Token", "password", true)
                                .placeholder("Paste the token shown once in Zendesk Admin Center")
                ),
                true,
                "https://support.zendesk.com/hc/en-us/articles/4408889192858",
                "1. In Zendesk Admin Center → Apps and integrations → APIs → API tokens, click \"Add API token\".\n" +
                "2. Set a description (e.g. \"Autotix\") and copy the generated token (shown once).\n" +
                "3. Paste the token + your Zendesk login email + your subdomain (the xxx in xxx.zendesk.com) below.\n" +
                "Docs: https://support.zendesk.com/hc/en-us/articles/4408889192858"
        );
    }
}
