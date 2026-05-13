package dev.autotix.domain.channel;

import java.time.Instant;
import java.util.Map;

/**
 * Value object storing the credentials needed to call back to the platform.
 *  Different platforms have different shapes:
 *    - OAuth: accessToken, refreshToken, expiresAt
 *    - API key: apiKey only
 *    - HMAC: clientId + secret
 *  Flexible attributes map covers platform-specific fields.
 *
 *  Persistence layer is responsible for encryption at rest.
 */
public final class ChannelCredential {

    private final String accessToken;
    private final String refreshToken;
    private final Instant expiresAt;
    private final Map<String, String> attributes;  // platform-specific extras

    public ChannelCredential(String accessToken, String refreshToken,
                             Instant expiresAt, Map<String, String> attributes) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.attributes = attributes;
    }

    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
    public Instant expiresAt() { return expiresAt; }
    public Map<String, String> attributes() { return attributes; }

    /**
     * Returns true if the credential has an expiresAt set AND it is before the given instant.
     * A null expiresAt means the credential does not expire.
     */
    public boolean isExpired(Instant now) {
        if (expiresAt == null) {
            return false;
        }
        return now.isAfter(expiresAt);
    }
}
