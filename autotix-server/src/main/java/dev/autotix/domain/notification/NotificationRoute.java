package dev.autotix.domain.notification;

import java.time.Instant;

/**
 * Aggregate root representing an admin-configured notification destination.
 *
 * Immutable-ish — mutators return void and update fields in place;
 * persistence layer rehydrates via setters.
 *
 * configJson format (channel-specific, raw JSON string):
 *   EMAIL:         {"to":["a@b.com"],"subjectTemplate":"[Autotix] SLA breached #{externalTicketId}"}
 *   SLACK_WEBHOOK: {"webhookUrl":"https://hooks.slack.com/...","messageTemplate":"SLA breached {ticketId}"}
 *
 * Template placeholders: {ticketId}, {externalTicketId}, {subject}, {customerIdentifier},
 *   {priority}, {status}, {breachedAt}, {ticketUrl}
 */
public final class NotificationRoute {

    private Long id;
    private String name;
    private NotificationEventKind eventKind;
    private NotificationChannel channel;
    private String configJson;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Long id() { return id; }
    public String name() { return name; }
    public NotificationEventKind eventKind() { return eventKind; }
    public NotificationChannel channel() { return channel; }
    public String configJson() { return configJson; }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    // Setters for persistence-layer rehydration
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setEventKind(NotificationEventKind eventKind) { this.eventKind = eventKind; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // -----------------------------------------------------------------------
    // Factories
    // -----------------------------------------------------------------------

    /** Create a new (not-yet-persisted) route. */
    public static NotificationRoute newRoute(String name,
                                             NotificationEventKind eventKind,
                                             NotificationChannel channel,
                                             String configJson,
                                             boolean enabled) {
        NotificationRoute r = new NotificationRoute();
        r.name = name;
        r.eventKind = eventKind;
        r.channel = channel;
        r.configJson = configJson;
        r.enabled = enabled;
        Instant now = Instant.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** Rehydrate from persistence. */
    public static NotificationRoute rehydrate(Long id, String name,
                                              NotificationEventKind eventKind,
                                              NotificationChannel channel,
                                              String configJson,
                                              boolean enabled,
                                              Instant createdAt,
                                              Instant updatedAt) {
        NotificationRoute r = new NotificationRoute();
        r.id = id;
        r.name = name;
        r.eventKind = eventKind;
        r.channel = channel;
        r.configJson = configJson;
        r.enabled = enabled;
        r.createdAt = createdAt;
        r.updatedAt = updatedAt;
        return r;
    }

    // -----------------------------------------------------------------------
    // Mutators
    // -----------------------------------------------------------------------

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void updateConfig(String configJson) {
        this.configJson = configJson;
        this.updatedAt = Instant.now();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void applyUpdate(String name, NotificationEventKind eventKind,
                            NotificationChannel channel, String configJson, boolean enabled) {
        this.name = name;
        this.eventKind = eventKind;
        this.channel = channel;
        this.configJson = configJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
