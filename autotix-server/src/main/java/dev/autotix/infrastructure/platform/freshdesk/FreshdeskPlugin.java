package dev.autotix.infrastructure.platform.freshdesk;

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
                        PlatformDescriptor.AuthField.of("domain", "Freshdesk Domain", "string", true)
                                .placeholder("acme.freshdesk.com")
                                .help("Your Freshdesk domain, e.g. acme.freshdesk.com"),
                        PlatformDescriptor.AuthField.of("api_key", "API Key", "password", true)
                                .placeholder("Your Freshdesk API key")
                ),
                false,
                "https://developers.freshdesk.com/api/",
                "1. Log in to Freshdesk.\n" +
                "2. Click your profile icon (top right) → Profile settings.\n" +
                "3. On the right side of the page, find the \"Your API Key\" panel and copy the key.\n" +
                "4. Paste the key and your Freshdesk domain (e.g. acme.freshdesk.com) below.\n" +
                "Docs: https://developers.freshdesk.com/api/"
        );
    }
}
