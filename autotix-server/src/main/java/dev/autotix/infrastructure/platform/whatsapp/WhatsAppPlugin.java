package dev.autotix.infrastructure.platform.whatsapp;

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
 * TODO: WhatsApp Business Cloud API integration (CHAT channel type).
 *  Auth: System User permanent access token (Meta for Developers).
 *  Webhook: X-Hub-Signature-256 (HMAC-SHA256 of payload with app secret).
 */
@Component
public class WhatsAppPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.WHATSAPP; }
    @Override public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: verify X-Hub-Signature-256; parse WhatsApp messages[].text.body
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST https://graph.facebook.com/v18.0/{phone_number_id}/messages
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // WhatsApp has no native ticket close; local-only no-op
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET https://graph.facebook.com/v18.0/{phone_number_id}
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.WHATSAPP,
                "WhatsApp Business",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("phone_number_id", "Phone Number ID", "string", true)
                                .placeholder("123456789012345")
                                .help("The numeric Phone Number ID from Meta for Developers → your WhatsApp app → API Setup"),
                        PlatformDescriptor.AuthField.of("business_account_id", "Business Account ID", "string", true)
                                .placeholder("987654321098765")
                                .help("WhatsApp Business Account ID (WABA ID) from Meta Business Manager"),
                        PlatformDescriptor.AuthField.of("access_token", "Access Token", "password", true)
                                .placeholder("EAAxxxxxxx…")
                                .help("System User permanent token (recommended) or temporary access token")
                ),
                false,
                "https://developers.facebook.com/docs/whatsapp/cloud-api/get-started",
                "1. Go to Meta for Developers (https://developers.facebook.com/) and open your WhatsApp app.\n" +
                "2. In the left sidebar click \"WhatsApp\" → \"API Setup\".\n" +
                "3. Copy the Phone number ID and WhatsApp Business Account ID shown on that page.\n" +
                "4. For a permanent token: go to Meta Business Manager → System Users → create a System User → Generate New Token → select your WhatsApp app with whatsapp_business_messaging permission.\n" +
                "5. Paste all three values below.\n" +
                "Docs: https://developers.facebook.com/docs/whatsapp/cloud-api/get-started"
        );
    }
}
