package dev.autotix.infrastructure.platform.wecom;

import dev.autotix.domain.channel.*;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * TODO: 企业微信 (WeCom) integration — Kefu (客服) module (CHAT channel type).
 *  Auth: corp_id + corp_secret -&gt; access_token (cached, 2h TTL).
 *  Webhook: AES-encrypted; signature msg_signature.
 */
@Component
public class WeComPlugin implements TicketPlatformPlugin {

    @Override public PlatformType platform() { return PlatformType.WECOM; }
    @Override public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // TODO: AES decrypt; verify signature; parse XML/JSON message
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // TODO: POST /cgi-bin/kf/send_msg
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        // TODO: POST /cgi-bin/kf/service_state/trans (终止会话)
    }

    @Override public boolean healthCheck(ChannelCredential credential) {
        // TODO: GET access_token endpoint
        throw new UnsupportedOperationException("TODO");
    }
}
