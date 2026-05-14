package dev.autotix.domain.ticket;

import java.time.Instant;

/**
 * Write-only audit record for meaningful ticket lifecycle events.
 *
 * actor format:
 *   "customer"        — inbound from customer
 *   "ai"             — AI-driven action
 *   "agent:{userId}" — human agent action
 *   "system"         — background job / automation
 *
 * details: free-form JSON string (serialized via Fastjson in repository impl).
 */
public class TicketActivity {

    private Long id;                      // null until persisted
    private final TicketId ticketId;
    private final String actor;
    private final TicketActivityAction action;
    private final String details;         // JSON map, may be null
    private final Instant occurredAt;

    public TicketActivity(TicketId ticketId, String actor,
                          TicketActivityAction action, String details,
                          Instant occurredAt) {
        if (ticketId == null) throw new IllegalArgumentException("ticketId must not be null");
        if (actor == null || actor.trim().isEmpty()) throw new IllegalArgumentException("actor must not be blank");
        if (action == null) throw new IllegalArgumentException("action must not be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt must not be null");
        this.ticketId = ticketId;
        this.actor = actor;
        this.action = action;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    /** Convenience ctor without details. */
    public TicketActivity(TicketId ticketId, String actor,
                          TicketActivityAction action, Instant occurredAt) {
        this(ticketId, actor, action, null, occurredAt);
    }

    public void assignPersistedId(Long id) {
        this.id = id;
    }

    public Long id() { return id; }
    public TicketId ticketId() { return ticketId; }
    public String actor() { return actor; }
    public TicketActivityAction action() { return action; }
    public String details() { return details; }
    public Instant occurredAt() { return occurredAt; }
}
