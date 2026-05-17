package dev.autotix.infrastructure.platform.wecom;

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
 * WeCom (企业微信) Customer Service (微信客服) integration.
 *
 * <h3>Inbound flow</h3>
 * WeCom POSTs an encrypted notification; {@link WecomWebhookHandler#handlePost} decrypts,
 * verifies, and pulls actual messages via {@code kf/sync_msg}.
 *
 * <h3>Outbound flow</h3>
 * {@link WecomClient#sendText} posts to {@code kf/send_msg}.
 *
 * <h3>Thread identity</h3>
 * {@link Ticket#externalNativeId()} stores {@code externalUserId@openKfId} so that
 * sendReply can split the two parts back out.
 *
 * <h3>GET verification</h3>
 * Handled by {@link WecomVerificationController} (separate controller on
 * {@code GET /v2/webhook/WECOM/{token}}) to avoid touching the generic POST controller.
 */
@Component
public class WeComPlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(WeComPlugin.class);

    private final WecomClient wecomClient;
    private final WecomWebhookHandler webhookHandler;

    @Autowired
    public WeComPlugin(WecomClient wecomClient, WecomWebhookHandler webhookHandler) {
        this.wecomClient = wecomClient;
        this.webhookHandler = webhookHandler;
    }

    @Override
    public PlatformType platform() { return PlatformType.WECOM; }

    @Override
    public ChannelType defaultChannelType() { return ChannelType.CHAT; }

    // -------------------------------------------------------------------------
    // Webhook
    // -------------------------------------------------------------------------

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        // Extract WeCom query params forwarded as pseudo-headers by WecomVerificationController
        // (for POST, the generic WebhookController passes these via the same header map)
        String msgSignature = headers.getOrDefault("x-wecom-msg-signature", "");
        String timestamp    = headers.getOrDefault("x-wecom-timestamp", "");
        String nonce        = headers.getOrDefault("x-wecom-nonce", "");

        List<TicketEvent> events = webhookHandler.handlePost(
                channel, msgSignature, timestamp, nonce, rawBody);

        if (events.isEmpty()) {
            return new TicketEvent(
                    channel.id(),
                    EventType.IGNORED,
                    "", "", "", "WeCom non-text event", rawBody,
                    Instant.now(),
                    Collections.<String, Object>emptyMap());
        }
        return events.get(0);
    }

    // -------------------------------------------------------------------------
    // Outbound
    // -------------------------------------------------------------------------

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        WecomCredentials creds = WecomCredentials.from(channel.credential());
        String externalNativeId = ticket.externalNativeId();

        if (externalNativeId == null || externalNativeId.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "WeCom sendReply: ticket.externalNativeId is blank; cannot identify recipient");
        }

        // externalNativeId format: externalUserId@openKfId
        String externalUserId;
        String openKfId;
        int atIdx = externalNativeId.lastIndexOf('@');
        if (atIdx > 0 && atIdx < externalNativeId.length() - 1) {
            externalUserId = externalNativeId.substring(0, atIdx);
            openKfId = externalNativeId.substring(atIdx + 1);
        } else {
            externalUserId = externalNativeId;
            openKfId = creds.openKfId;
        }

        try {
            String accessToken = wecomClient.getAccessToken(creds.corpId, creds.secret);
            wecomClient.sendText(accessToken, externalUserId, openKfId, formattedReply);
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.IntegrationException(
                    "wecom", "sendReply failed for ticket " + externalNativeId, e);
        }
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        log.debug("[WeCom] close() called for ticket {}; no-op", ticket.externalNativeId());
    }

    // -------------------------------------------------------------------------
    // Health check
    // -------------------------------------------------------------------------

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        WecomCredentials creds = WecomCredentials.from(credential);
        return wecomClient.ping(creds.corpId, creds.secret);
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

    @Override
    public PlatformDescriptor descriptor() {
        return new PlatformDescriptor(
                PlatformType.WECOM,
                "WeCom 微信客服",
                "chat",
                ChannelType.CHAT,
                Collections.singletonList(ChannelType.CHAT),
                PlatformDescriptor.AuthMethod.API_KEY,
                Arrays.asList(
                        PlatformDescriptor.AuthField.of("corpid", "Corp ID (企业ID)", "string", true)
                                .placeholder("ww1234567890abcdef")
                                .help("企业微信管理后台 → 我的企业 → 企业信息页获取"),
                        PlatformDescriptor.AuthField.of("secret", "App Secret", "password", true)
                                .placeholder("Paste 微信客服 App Secret")
                                .help("企业微信管理后台 → 应用管理 → 微信客服 → API → Secret"),
                        PlatformDescriptor.AuthField.of("token", "Callback Token", "string", true)
                                .placeholder("自选一段字符串，如 autotix_token_xyz")
                                .help("应用回调配置中的 Token，与 WeCom 后台填写的值保持一致"),
                        PlatformDescriptor.AuthField.of("encoding_aes_key", "EncodingAESKey", "password", true)
                                .placeholder("43位 Base64 字符")
                                .help("应用回调配置 → EncodingAESKey；点「随机生成」后复制"),
                        PlatformDescriptor.AuthField.of("open_kfid", "Open KfId (客服账号ID)", "string", true)
                                .placeholder("wkxxxxxxxxxxxxxxxx")
                                .help("企业微信管理后台 → 应用管理 → 微信客服 → 客服账号 ID")
                ),
                true,
                "https://developer.work.weixin.qq.com/document/path/94668",
                "1. 企业微信管理后台 → 应用管理 → 微信客服 → 创建客服账号，记下 Open KfId.\n" +
                "2. 我的企业 → 企业信息 → 复制 Corp ID.\n" +
                "3. 应用管理 → 微信客服 → API → 复制 Secret.\n" +
                "4. 在 Autotix 创建 WeCom 频道，填入以上信息，Token 自选字符串，EncodingAESKey 点「随机生成」复制.\n" +
                "5. 频道创建成功后，将页面上显示的 Inbound URL 填入：企业微信后台 → 微信客服 → API 接收消息配置 → URL.\n" +
                "6. 点「保存」——WeCom 会立即 GET 验证 URL，Autotix 自动通过."
        );
    }
}
