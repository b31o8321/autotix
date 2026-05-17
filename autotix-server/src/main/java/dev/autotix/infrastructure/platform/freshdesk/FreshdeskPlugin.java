package dev.autotix.infrastructure.platform.freshdesk;

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
 * Freshdesk integration (EMAIL channel type).
 *
 * <p>Auth: HTTP Basic {@code {api_key}:X} (Freshdesk convention).
 *
 * <p>Inbound: Freshdesk Automation → Webhook action. Payload verified via optional
 * {@code X-Autotix-Webhook-Token} header (see {@link FreshdeskWebhookParser}).
 *
 * <p>Outbound reply: {@code POST /api/v2/tickets/{id}/reply}.
 * Close: {@code PUT /api/v2/tickets/{id}} with status=5 (Closed).
 */
@Component
public class FreshdeskPlugin implements TicketPlatformPlugin {

    private final FreshdeskClient freshdeskClient;
    private final FreshdeskWebhookParser webhookParser;

    @Autowired
    public FreshdeskPlugin(FreshdeskClient freshdeskClient, FreshdeskWebhookParser webhookParser) {
        this.freshdeskClient = freshdeskClient;
        this.webhookParser = webhookParser;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.FRESHDESK;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        return webhookParser.parseAndVerify(channel, headers, rawBody);
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        try {
            freshdeskClient.replyToTicket(
                    channel.credential(),
                    ticket.externalNativeId(),
                    formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "freshdesk", "sendReply failed for ticket " + ticket.externalNativeId(), e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        try {
            freshdeskClient.updateTicketStatus(
                    channel.credential(),
                    ticket.externalNativeId(),
                    5 /* Closed */);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "freshdesk", "close failed for ticket " + ticket.externalNativeId(), e);
        }
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        return freshdeskClient.ping(credential);
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.FRESHDESK,
                "Freshdesk",
                "ticket",
                ChannelType.EMAIL,
                Collections.singletonList(ChannelType.EMAIL),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("domain", "Freshdesk Subdomain", "string", true)
                                .placeholder("acme")
                                .help("The subdomain part of your Freshdesk URL: acme.freshdesk.com"),
                        PlatformDescriptor.AuthField.of("api_key", "API Key", "password", true)
                                .placeholder("Paste your Freshdesk API key")
                                .help("Profile (top-right) → Profile settings → Your API Key"),
                        PlatformDescriptor.AuthField.of("webhook_secret", "Webhook Token", "password", false)
                                .placeholder("(optional — sent in X-Autotix-Webhook-Token header)")
                                .help("If set, incoming webhooks must include this value in the X-Autotix-Webhook-Token header. " +
                                      "Configure this header in Freshdesk Automation → Webhook → Custom Headers.")
                ),
                true,
                "https://developers.freshdesk.com/api/",
                "1. In Freshdesk, click your profile icon (top right) → Profile settings. " +
                "Find 'Your API Key' on the right and copy it.\n" +
                "2. Admin → Workflows → Automations → Ticket Updates / Creation. Create a rule with the trigger you need " +
                "(e.g. New Ticket Created), then add action 'Trigger Webhook': " +
                "Request URL = the Inbound URL shown after channel creation, Content Type = JSON.\n" +
                "3. (Optional) In the webhook action → Custom Headers, add a header " +
                "'X-Autotix-Webhook-Token: <your secret>' and paste the same value in the Webhook Token field below.\n" +
                "Docs: https://developers.freshdesk.com/api/"
        );
    }
}
