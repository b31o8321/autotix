package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;

import java.time.Instant;

/**
 * Value object representing a single message in a ticket thread.
 *  - Immutable
 *  - content is platform-agnostic plain text or markdown (raw form received/sent)
 *  - author identifies who said it: "customer", "ai", "agent:{id}"
 *  - visibility: PUBLIC (default, sent externally) or INTERNAL (internal note only)
 */
public final class Message {

    private final MessageDirection direction;
    private final String author;
    private final String content;
    private final Instant occurredAt;
    private final MessageVisibility visibility;

    /** Full constructor with explicit visibility. */
    public Message(MessageDirection direction, String author, String content,
                   Instant occurredAt, MessageVisibility visibility) {
        if (direction == null) {
            throw new AutotixException.ValidationException("direction must not be null");
        }
        if (author == null || author.trim().isEmpty()) {
            throw new AutotixException.ValidationException("author must not be blank");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new AutotixException.ValidationException("content must not be blank");
        }
        if (occurredAt == null) {
            throw new AutotixException.ValidationException("occurredAt must not be null");
        }
        if (visibility == null) {
            throw new AutotixException.ValidationException("visibility must not be null");
        }
        this.direction = direction;
        this.author = author;
        this.content = content.trim();
        this.occurredAt = occurredAt;
        this.visibility = visibility;
    }

    /**
     * Backward-compatible constructor — defaults visibility to PUBLIC.
     * All existing callers use this form.
     */
    public Message(MessageDirection direction, String author, String content, Instant occurredAt) {
        this(direction, author, content, occurredAt, MessageVisibility.PUBLIC);
    }

    public MessageDirection direction() { return direction; }
    public String author() { return author; }
    public String content() { return content; }
    public Instant occurredAt() { return occurredAt; }
    public MessageVisibility visibility() { return visibility; }

    public boolean isInternal() {
        return visibility == MessageVisibility.INTERNAL;
    }
}
