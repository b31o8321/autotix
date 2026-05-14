package dev.autotix.domain.ticket;

/**
 * Actions that can appear in the ticket activity log.
 * Each value corresponds to a meaningful event in the ticket lifecycle.
 */
public enum TicketActivityAction {
    CREATED,
    REOPENED,
    STATUS_CHANGED,
    ASSIGNED,
    UNASSIGNED,
    TAGS_CHANGED,
    PRIORITY_CHANGED,
    TYPE_CHANGED,
    REPLIED_PUBLIC,
    REPLIED_INTERNAL,
    SOLVED,
    PERMANENTLY_CLOSED,
    MARKED_SPAM,
    SPAWNED,
    SLA_BREACHED,
    ESCALATED,
    AI_RESUMED
}
