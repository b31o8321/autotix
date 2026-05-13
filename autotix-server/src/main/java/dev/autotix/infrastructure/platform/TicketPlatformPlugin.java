package dev.autotix.infrastructure.platform;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;

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
}
