package dev.autotix.domain.ticket;

import java.util.Objects;

/**
 * TODO: Value object for Ticket identity.
 *  - Wrap internal long/UUID id
 *  - Provide factory from external platform's native ticket id
 */
public final class TicketId {

    private final String value;

    public TicketId(String value) {
        // TODO: validate non-null, non-empty
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TicketId)) return false;
        return Objects.equals(value, ((TicketId) o).value);
    }
    @Override public int hashCode() { return Objects.hash(value); }
}
