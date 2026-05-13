package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;

import java.time.Instant;

/**
 * Value object representing a single message in a ticket thread.
 *  - Immutable
 *  - content is platform-agnostic plain text or markdown (raw form received/sent)
 *  - author identifies who said it: "customer", "ai", "agent:{id}"
 */
public final class Message {

    private final MessageDirection direction;
    private final String author;
    private final String content;
    private final Instant occurredAt;

    public Message(MessageDirection direction, String author, String content, Instant occurredAt) {
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
        this.direction = direction;
        this.author = author;
        this.content = content.trim();
        this.occurredAt = occurredAt;
    }

    public MessageDirection direction() { return direction; }
    public String author() { return author; }
    public String content() { return content; }
    public Instant occurredAt() { return occurredAt; }
}
