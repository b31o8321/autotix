package dev.autotix.infrastructure.platform.wecom;

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

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.WECOM,
                "WeCom (企业微信)",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("corp_id", "Corp ID", "string", true)
                                .placeholder("ww…")
                                .help("企业ID，在企业微信管理后台首页可查"),
                        PlatformDescriptor.AuthField.of("corp_secret", "Corp Secret", "password", true)
                                .placeholder("应用的Secret")
                                .help("自建应用的Secret"),
                        PlatformDescriptor.AuthField.of("agent_id", "Agent ID", "string", false)
                                .placeholder("1000001")
                                .help("自建应用的AgentId（可选）")
                ),
                false,
                "https://developer.work.weixin.qq.com/document/path/90556",
                "1. 登录企业微信管理后台 (https://work.weixin.qq.com/wework_admin/)。\n" +
                "2. 进入「应用管理」→「自建应用」，选择或创建应用。\n" +
                "3. 在应用详情页复制「AgentId」。\n" +
                "4. 点击应用下的「查看」（Secret），扫码后复制 Secret。\n" +
                "5. 在首页「我的企业」→「企业信息」复制企业 ID（CorpID）。\n" +
                "Docs: https://developer.work.weixin.qq.com/document/path/90556"
        );
    }
}
