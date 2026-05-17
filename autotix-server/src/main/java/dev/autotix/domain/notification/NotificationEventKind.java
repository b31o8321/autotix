package dev.autotix.domain.notification;

/**
 * Kinds of system events that can trigger notification routes.
 * Extend this enum to add new event triggers (e.g. TICKET_ESCALATED, NEW_HIGH_PRIORITY).
 */
public enum NotificationEventKind {
    SLA_BREACHED
}
