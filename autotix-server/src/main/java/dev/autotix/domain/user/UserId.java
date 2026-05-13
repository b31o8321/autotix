package dev.autotix.domain.user;

import dev.autotix.domain.AutotixException;

import java.util.Objects;

/**
 * Value object for User identity.
 */
public final class UserId {

    private final String value;

    public UserId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new AutotixException.ValidationException("UserId must not be null or empty");
        }
        this.value = value;
    }

    public String value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId)) return false;
        return Objects.equals(value, ((UserId) o).value);
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return "UserId(" + value + ")"; }
}
