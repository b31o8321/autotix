package dev.autotix.domain.ticket;

/**
 * Visibility of a message within the ticket thread.
 *
 * PUBLIC   — sent externally to the customer platform (normal reply).
 * INTERNAL — internal note, never sent externally; visible to agents only.
 */
public enum MessageVisibility {
    PUBLIC, INTERNAL
}
