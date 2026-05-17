package dev.autotix.infrastructure.platform.line;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LINE Messaging API integration (CHAT channel type).
 *
 * <p>Auth: {@code channel_access_token} (long-lived) + {@code channel_secret} for webhook HMAC.
 *
 * <p>Inbound: POST /v2/webhook/LINE/{token} — body signed with {@code X-Line-Signature}.
 *
 * <p>Outbound: POST /v2/bot/message/push — uses access token, targets LINE userId stored
 * in {@link Ticket#externalNativeId()}.
 *
 * <p>If a single POST contains multiple events, only the first text-message event is returned.
 * Subsequent events in the same POST are silently dropped; LINE retries are idempotent.
 */
@Component
public class LinePlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(LinePlugin.class);

    private final LineClient lineClient;
    private final LineWebhookParser webhookParser;

    @Autowired
    public LinePlugin(LineClient lineClient, LineWebhookParser webhookParser) {
        this.lineClient = lineClient;
        this.webhookParser = webhookParser;
    }

    @Override
    public PlatformType platform() { return PlatformType.LINE; }

    @Override
    public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    // -------------------------------------------------------------------------
    // Webhook
    // -------------------------------------------------------------------------

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        List<TicketEvent> events = webhookParser.parseAndVerify(channel, headers, rawBody);
        if (events.isEmpty()) {
            // Non-text event types (follow, unfollow, postback, sticker) — return ignored sentinel
            return new TicketEvent(
                    channel.id(),
                    EventType.IGNORED,
                    "",
                    "",
                    "",
                    "LINE non-text event",
                    rawBody,
                    Instant.now(),
                    Collections.<String, Object>emptyMap()
            );
        }
        return events.get(0);
    }

    // -------------------------------------------------------------------------
    // Outbound
    // -------------------------------------------------------------------------

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        LineCredentials creds = LineCredentials.from(channel.credential());
        String lineUserId = ticket.externalNativeId();
        if (lineUserId == null || lineUserId.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "LINE sendReply: ticket.externalNativeId is blank; cannot identify recipient");
        }
        try {
            lineClient.pushText(creds.channelAccessToken, lineUserId, formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "line", "sendReply failed for ticket " + ticket.externalNativeId(), e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // LINE has no native ticket-close concept — local-only no-op
        log.debug("[LINE] close() called for ticket {}; no-op (LINE has no thread closure API)",
                ticket.externalNativeId());
    }

    // -------------------------------------------------------------------------
    // Health check
    // -------------------------------------------------------------------------

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        LineCredentials creds = LineCredentials.from(credential);
        return lineClient.ping(creds);
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

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
                        PlatformDescriptor.AuthField.of("channel_access_token", "Channel Access Token", "password", true)
                                .placeholder("Paste long-lived channel access token")
                                .help("LINE Developer Console → Messaging API tab → Channel access token (long-lived) → Issue"),
                        PlatformDescriptor.AuthField.of("channel_secret", "Channel Secret", "password", true)
                                .placeholder("Paste channel secret")
                                .help("LINE Developer Console → Basic settings → Channel secret")
                ),
                true,
                "https://developers.line.biz/en/reference/messaging-api/",
                "1. https://developers.line.biz/console → create Provider → create Messaging API channel.\n" +
                "2. Basic settings → copy Channel secret.\n" +
                "3. Messaging API → Issue Channel access token (long-lived) → copy.\n" +
                "4. Messaging API → Webhook URL → paste the Inbound Webhook URL shown after channel is created.\n" +
                "5. Enable 'Use webhook' toggle.\n" +
                "6. Disable 'Auto-reply messages' under Response settings (otherwise LINE's default bot replies will collide)."
        );
    }
}
