package dev.autotix.domain.channel;

import dev.autotix.domain.AutotixException;

import java.time.Instant;
import java.util.UUID;

/**
 * Aggregate root for a connected platform integration instance.
 *  One Channel = one specific account on a specific platform.
 *  E.g. "Acme's Zendesk tickets" vs "Acme's Zendesk Sunshine" are TWO Channels,
 *  with the same platform but different ChannelType.
 */
public class Channel {

    private ChannelId id;
    private PlatformType platform;
    private ChannelType type;             // EMAIL / CHAT (per integration instance)
    private String displayName;           // user-facing label
    private String webhookToken;          // random token used in /v2/webhook/{platform}/{token}
    private ChannelCredential credential;
    private boolean enabled;
    private boolean autoReplyEnabled;     // AI auto-reply on/off
    private Instant createdAt;
    private Instant updatedAt;

    /** Private constructor — use factory methods or rehydration. */
    private Channel() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /** Create a new disabled Channel with a freshly-generated webhookToken. */
    public static Channel newInstance(PlatformType platform, ChannelType type, String displayName) {
        if (platform == null) {
            throw new AutotixException.ValidationException("platform must not be null");
        }
        if (type == null) {
            throw new AutotixException.ValidationException("type must not be null");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new AutotixException.ValidationException("displayName must not be blank");
        }
        Instant now = Instant.now();
        Channel c = new Channel();
        c.platform = platform;
        c.type = type;
        c.displayName = displayName.trim();
        c.webhookToken = generateToken();
        c.enabled = false;
        c.autoReplyEnabled = false;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    // -----------------------------------------------------------------------
    // Rehydration (called by repository impl)
    // -----------------------------------------------------------------------

    public static Channel rehydrate(ChannelId id, PlatformType platform, ChannelType type,
                                    String displayName, String webhookToken,
                                    ChannelCredential credential, boolean enabled,
                                    boolean autoReplyEnabled, Instant createdAt, Instant updatedAt) {
        Channel c = new Channel();
        c.id = id;
        c.platform = platform;
        c.type = type;
        c.displayName = displayName;
        c.webhookToken = webhookToken;
        c.credential = credential;
        c.enabled = enabled;
        c.autoReplyEnabled = autoReplyEnabled;
        c.createdAt = createdAt;
        c.updatedAt = updatedAt;
        return c;
    }

    // -----------------------------------------------------------------------
    // Package-private id injection (called by repository after INSERT)
    // -----------------------------------------------------------------------

    public void assignPersistedId(ChannelId id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Domain behaviors
    // -----------------------------------------------------------------------

    /** Update credential after OAuth callback; enable channel. */
    public void connect(ChannelCredential credential) {
        if (credential == null) {
            throw new AutotixException.ValidationException("credential must not be null");
        }
        this.credential = credential;
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Revoke credential, set enabled=false.
     * Credential is cleared (set to null) on disconnect.
     */
    public void disconnect() {
        this.credential = null;
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    /** Toggle AI auto reply. */
    public void setAutoReply(boolean enabled) {
        this.autoReplyEnabled = enabled;
        this.updatedAt = Instant.now();
    }

    /** Rotate webhookToken (e.g. on compromise). */
    public void rotateWebhookToken() {
        this.webhookToken = generateToken();
        this.updatedAt = Instant.now();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public ChannelId id() { return id; }
    public PlatformType platform() { return platform; }
    public ChannelType type() { return type; }
    public String displayName() { return displayName; }
    public String webhookToken() { return webhookToken; }
    public ChannelCredential credential() { return credential; }
    public boolean isEnabled() { return enabled; }
    public boolean isAutoReplyEnabled() { return autoReplyEnabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
