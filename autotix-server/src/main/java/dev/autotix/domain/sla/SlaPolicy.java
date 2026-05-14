package dev.autotix.domain.sla;

import dev.autotix.domain.ticket.TicketPriority;

import java.time.Instant;

/**
 * SLA policy: specifies first-response and resolution time targets for a given ticket priority.
 * One policy per TicketPriority. If no policy exists, callers fall back to defaults.
 *
 * Defaults (when no DB row):
 *   LOW    — 480 min first response / 2880 min resolution (8h / 48h)
 *   NORMAL — 240 min / 1440 min (4h / 24h)
 *   HIGH   — 60 min  / 480 min  (1h / 8h)
 *   URGENT — 30 min  / 240 min  (30m / 4h)
 */
public class SlaPolicy {

    private String id;
    private String name;
    private TicketPriority priority;
    private int firstResponseMinutes;
    private int resolutionMinutes;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    /** Private constructor — use factory. */
    private SlaPolicy() {}

    public static SlaPolicy create(String name, TicketPriority priority,
                                   int firstResponseMinutes, int resolutionMinutes,
                                   boolean enabled) {
        SlaPolicy p = new SlaPolicy();
        p.name = name;
        p.priority = priority;
        p.firstResponseMinutes = firstResponseMinutes;
        p.resolutionMinutes = resolutionMinutes;
        p.enabled = enabled;
        Instant now = Instant.now();
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public static SlaPolicy rehydrate(String id, String name, TicketPriority priority,
                                      int firstResponseMinutes, int resolutionMinutes,
                                      boolean enabled, Instant createdAt, Instant updatedAt) {
        SlaPolicy p = new SlaPolicy();
        p.id = id;
        p.name = name;
        p.priority = priority;
        p.firstResponseMinutes = firstResponseMinutes;
        p.resolutionMinutes = resolutionMinutes;
        p.enabled = enabled;
        p.createdAt = createdAt;
        p.updatedAt = updatedAt;
        return p;
    }

    /** Assign persisted id after INSERT. */
    public void assignPersistedId(String id) {
        this.id = id;
    }

    // Accessors
    public String id() { return id; }
    public String name() { return name; }
    public TicketPriority priority() { return priority; }
    public int firstResponseMinutes() { return firstResponseMinutes; }
    public int resolutionMinutes() { return resolutionMinutes; }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    // Mutators
    public void update(String name, int firstResponseMinutes, int resolutionMinutes, boolean enabled) {
        this.name = name;
        this.firstResponseMinutes = firstResponseMinutes;
        this.resolutionMinutes = resolutionMinutes;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
