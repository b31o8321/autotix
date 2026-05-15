package dev.autotix.infrastructure.platform.gorgias;

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
 * TODO: Gorgias helpdesk integration (EMAIL channel type).
 *  Auth: HTTP Basic — username (email) + API key.
 *  Webhook: HMAC-SHA256 signature via X-Gorgias-Signature-256 header.
 */
@Component
public class GorgiasPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.GORGIAS; }
    @Override public ChannelType defaultChannelType() { return ChannelType.EMAIL; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: verify HMAC-SHA256; parse ticket.message.created event
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST /api/tickets/{id}/messages
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: PUT /api/tickets/{id} with status=closed
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET /api/users/me
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.GORGIAS,
                "Gorgias",
                "ticket",
                ChannelType.EMAIL,
                Collections.singletonList(ChannelType.EMAIL),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("domain", "Gorgias Domain", "string", true)
                                .placeholder("acme.gorgias.com")
                                .help("Your Gorgias subdomain, e.g. acme.gorgias.com"),
                        PlatformDescriptor.AuthField.of("username", "Username (Email)", "string", true)
                                .placeholder("you@yourcompany.com")
                                .help("The email address of your Gorgias account"),
                        PlatformDescriptor.AuthField.of("api_key", "API Key", "password", true)
                                .placeholder("Your Gorgias REST API key")
                ),
                false,
                "https://developers.gorgias.com/reference/introduction",
                "1. Log in to Gorgias and click your profile icon (bottom left) → Settings.\n" +
                "2. Navigate to Settings → REST API.\n" +
                "3. Click \"Reveal credentials\" — copy the API key and note your username (email) and domain.\n" +
                "4. Paste the domain, username, and API key below.\n" +
                "Docs: https://developers.gorgias.com/reference/introduction"
        );
    }
}
