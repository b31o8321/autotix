package dev.autotix.domain.customer;

import dev.autotix.domain.channel.ChannelId;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing a single identifier for a customer on a specific channel.
 * Immutable.
 */
public final class CustomerIdentifier {

    private final CustomerIdentifierType type;
    private final String value;         // normalized (email lowercased)
    private final ChannelId channelId;  // nullable — the channel where first seen
    private final Instant firstSeenAt;

    public CustomerIdentifier(CustomerIdentifierType type, String value,
                              ChannelId channelId, Instant firstSeenAt) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (firstSeenAt == null) {
            throw new IllegalArgumentException("firstSeenAt must not be null");
        }
        this.type = type;
        this.value = normalizeValue(type, value);
        this.channelId = channelId;
        this.firstSeenAt = firstSeenAt;
    }

    /** Normalize: lowercase for EMAIL, trim for all. */
    private static String normalizeValue(CustomerIdentifierType type, String raw) {
        String trimmed = raw.trim();
        return type == CustomerIdentifierType.EMAIL ? trimmed.toLowerCase() : trimmed;
    }

    public CustomerIdentifierType type() { return type; }
    public String value() { return value; }
    public ChannelId channelId() { return channelId; }
    public Instant firstSeenAt() { return firstSeenAt; }

    /** Two identifiers are considered the same if (type, value) match. */
    public boolean sameIdentity(CustomerIdentifierType otherType, String otherValue) {
        String normalized = normalizeValue(otherType, otherValue != null ? otherValue : "");
        return type == otherType && Objects.equals(value, normalized);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerIdentifier)) return false;
        CustomerIdentifier that = (CustomerIdentifier) o;
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }
}
