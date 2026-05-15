package dev.autotix.infrastructure.platform.line;

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

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.LINE,
                "LINE",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("channel_id", "Channel ID", "string", true)
                                .placeholder("1234567890")
                                .help("Numeric Channel ID from LINE Developers Console"),
                        PlatformDescriptor.AuthField.of("channel_secret", "Channel Secret", "password", true)
                                .placeholder("Channel secret from LINE Developers Console"),
                        PlatformDescriptor.AuthField.of("channel_access_token", "Channel Access Token (Long-lived)", "password", true)
                                .placeholder("Long-lived channel access token")
                ),
                false,
                "https://developers.line.biz/en/docs/messaging-api/",
                "1. Go to LINE Developers Console (https://developers.line.biz/) and log in.\n" +
                "2. Select your provider and open your Messaging API channel.\n" +
                "3. Under the \"Basic settings\" tab, copy the Channel ID and Channel secret.\n" +
                "4. Go to the \"Messaging API\" tab, scroll to \"Channel access token (long-lived)\" and click \"Issue\".\n" +
                "5. Copy the issued token and paste all three values below.\n" +
                "Docs: https://developers.line.biz/en/docs/messaging-api/"
        );
    }
}
