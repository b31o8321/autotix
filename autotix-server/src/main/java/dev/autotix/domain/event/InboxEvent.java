package dev.autotix.domain.event;

import java.time.Instant;

/**
 * TODO: Server-pushed event for the live Inbox view.
 *  Published from application layer when meaningful state changes occur:
 *    TICKET_CREATED  — new ticket arrived
 *    AI_REPLIED      — AI auto-reply sent
 *    AGENT_REPLIED   — human agent sent reply (sync across multiple admin tabs)
 *    STATUS_CHANGED  — status transition
 *    ASSIGNED        — assignee changed
 *
 *  Transport: SSE (text/event-stream). One stream per logged-in agent.
 *  Filter: server-side filter so VIEWER/AGENT only see events they're allowed to see.
 */
public final class InboxEvent {

    public final Kind kind;
    public final String ticketId;
    public final String channelId;
    public final String summary;       // short text for UI display
    public final Instant occurredAt;

    public InboxEvent(Kind kind, String ticketId, String channelId, String summary, Instant occurredAt) {
        this.kind = kind;
        this.ticketId = ticketId;
        this.channelId = channelId;
        this.summary = summary;
        this.occurredAt = occurredAt;
    }

    public enum Kind {
        TICKET_CREATED,
        AI_REPLIED,
        AGENT_REPLIED,
        STATUS_CHANGED,
        ASSIGNED
    }
}
