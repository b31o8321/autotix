package dev.autotix.infrastructure.platform;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformDescriptor;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * TODO: SPI implemented by each platform integration.
 *  Acts as ANTI-CORRUPTION LAYER between third-party APIs and our domain.
 *
 *  Lifecycle:
 *    1) parseWebhook  — convert raw payload to TicketEvent
 *    2) sendReply     — push our outbound message back to platform
 *    3) close         — close ticket on the platform side
 *    4) OAuth helpers — startOAuth / exchangeCode (optional, only for OAuth platforms)
 *
 *  Each implementation is a Spring @Component and registered via {@link PluginRegistry}.
 */
public interface TicketPlatformPlugin {

    /** TODO: identifies which PlatformType this plugin handles. */
    PlatformType platform();

    /** TODO: default channel type for this plugin (some platforms instance-driven; see Channel). */
    ChannelType defaultChannelType();

    /**
     * TODO: verify signature/HMAC of raw request, then parse to TicketEvent.
     *  Throw on invalid signature.
     */
    TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody);

    /**
     * TODO: push outbound reply to the platform (Markdown already formatted to channel-appropriate
     *  format by ReplyFormatter before reaching here).
     */
    void sendReply(Channel channel, Ticket ticket, String formattedReply);

    /**
     * E2E-B: Extended send that also returns the platform-generated message ID (e.g. SMTP Message-ID).
     * Default implementation calls sendReply() and returns SendResult with null externalMessageId.
     * Override in EmailPlugin to capture the SMTP Message-ID for threading.
     */
    default SendResult sendReplyDetailed(Channel channel, Ticket ticket, String formattedReply) {
        sendReply(channel, ticket, formattedReply);
        return new SendResult(null);
    }

    /**
     * E2E-B: Result of sendReplyDetailed.
     */
    final class SendResult {
        public final String externalMessageId;  // nullable; non-null only for email
        public SendResult(String externalMessageId) {
            this.externalMessageId = externalMessageId;
        }
    }

    /** TODO: close ticket on platform side. */
    void close(Channel channel, Ticket ticket);

    /** TODO: optional — only OAuth-based plugins. Default: throw unsupported. */
    default String startOAuth(String state) {
        throw new UnsupportedOperationException(platform() + " does not support OAuth");
    }

    /** TODO: optional — exchange code -&gt; credential. */
    default ChannelCredential exchangeCode(String code) {
        throw new UnsupportedOperationException(platform() + " does not support OAuth");
    }

    /** TODO: cheap call to verify credentials are valid (used by connectWithApiKey). */
    boolean healthCheck(ChannelCredential credential);

    /**
     * Returns the platform's descriptor (display name, category, auth method, field schema).
     * Override in each plugin for a precise schema.
     * Default derives a minimal API_KEY stub descriptor for platforms with no custom implementation.
     */
    default PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                platform(),
                platform().name().charAt(0) + platform().name().substring(1).toLowerCase().replace('_', ' '),
                "other",
                defaultChannelType(),
                Collections.singletonList(defaultChannelType()),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("apiKey", "API Key", "password", true)
                                .placeholder("Your API key")
                ),
                false,
                null
        );
    }
}
