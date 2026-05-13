package dev.autotix.domain.channel;

import java.util.Objects;

/**
 * TODO: Value object for Channel identity (one per platform integration instance).
 */
public final class ChannelId {

    private final String value;

    public ChannelId(String value) {
        // TODO: validate non-null, non-empty
        this.value = value;
    }

    public String value() { return value; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelId)) return false;
        return Objects.equals(value, ((ChannelId) o).value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
