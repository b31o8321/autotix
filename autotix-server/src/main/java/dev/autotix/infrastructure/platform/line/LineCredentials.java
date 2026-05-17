package dev.autotix.infrastructure.platform.line;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelCredential;

import java.util.Map;

/**
 * Value-object that extracts LINE credentials from a {@link ChannelCredential}.
 */
public final class LineCredentials {

    public final String channelAccessToken;
    public final String channelSecret;

    private LineCredentials(String channelAccessToken, String channelSecret) {
        this.channelAccessToken = channelAccessToken;
        this.channelSecret = channelSecret;
    }

    public static LineCredentials from(ChannelCredential credential) {
        Map<String, String> attrs = credential == null ? null : credential.attributes();
        if (attrs == null) {
            throw new AutotixException.ValidationException(
                    "LINE credential attributes are null");
        }
        String token = attrs.get("channel_access_token");
        String secret = attrs.get("channel_secret");
        if (token == null || token.trim().isEmpty()) {
            throw new AutotixException.ValidationException(
                    "LINE credential missing 'channel_access_token'");
        }
        if (secret == null || secret.trim().isEmpty()) {
            throw new AutotixException.ValidationException(
                    "LINE credential missing 'channel_secret'");
        }
        return new LineCredentials(token.trim(), secret.trim());
    }
}
