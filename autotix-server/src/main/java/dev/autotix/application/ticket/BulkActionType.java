package dev.autotix.application.ticket;

/**
 * Discriminant for the bulk ticket action endpoint.
 */
public enum BulkActionType {
    STATUS_CHANGE,
    ASSIGN,
    UNASSIGN,
    ADD_TAG,
    REMOVE_TAG,
    SOLVE,
    MARK_SPAM
}
