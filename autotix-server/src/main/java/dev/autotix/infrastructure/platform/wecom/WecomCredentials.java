package dev.autotix.infrastructure.platform.wecom;

import dev.autotix.domain.channel.ChannelCredential;

/**
 * Typed view of WeCom (企业微信客服) channel credentials.
 *
 * <ul>
 *   <li>{@code corpid}           — enterprise Corp ID (企业ID)
 *   <li>{@code secret}           — 微信客服 App Secret
 *   <li>{@code token}            — admin-chosen callback token for signature verification
 *   <li>{@code encoding_aes_key} — 43-char Base64 AES key for message encryption
 *   <li>{@code open_kfid}        — Customer Service account ID (用于发消息)
 * </ul>
 */
public final class WecomCredentials {

    public final String corpId;
    public final String secret;
    public final String token;
    public final String encodingAesKey;
    public final String openKfId;

    private WecomCredentials(String corpId, String secret, String token,
                              String encodingAesKey, String openKfId) {
        this.corpId = corpId;
        this.secret = secret;
        this.token = token;
        this.encodingAesKey = encodingAesKey;
        this.openKfId = openKfId;
    }

    public static WecomCredentials from(ChannelCredential credential) {
        return new WecomCredentials(
                get(credential, "corpid"),
                get(credential, "secret"),
                get(credential, "token"),
                get(credential, "encoding_aes_key"),
                get(credential, "open_kfid")
        );
    }

    private static String get(ChannelCredential credential, String key) {
        if (credential == null || credential.attributes() == null) return "";
        String v = credential.attributes().get(key);
        return v != null ? v : "";
    }
}
