package dev.autotix.domain.customer;

import java.util.Objects;

/**
 * Value object for Customer identity (Long primary key wrapped as String for consistency).
 */
public final class CustomerId {

    private final String value;

    public CustomerId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("CustomerId value must not be blank");
        }
        this.value = value;
    }

    public CustomerId(Long value) {
        this(String.valueOf(value));
    }

    public String value() {
        return value;
    }

    public Long longValue() {
        return Long.parseLong(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerId)) return false;
        return Objects.equals(value, ((CustomerId) o).value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "CustomerId(" + value + ")";
    }
}
