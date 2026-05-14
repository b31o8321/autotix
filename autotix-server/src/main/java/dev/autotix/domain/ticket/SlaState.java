package dev.autotix.domain.ticket;

/**
 * Derived view of current SLA state for a ticket.
 * Positive remainingMs = time still left. Negative = overdue.
 * Long.MAX_VALUE means no deadline is set.
 */
public class SlaState {

    private final long firstResponseRemainingMs;
    private final long resolutionRemainingMs;
    private final boolean breached;

    public SlaState(long firstResponseRemainingMs, long resolutionRemainingMs, boolean breached) {
        this.firstResponseRemainingMs = firstResponseRemainingMs;
        this.resolutionRemainingMs = resolutionRemainingMs;
        this.breached = breached;
    }

    public long firstResponseRemainingMs() { return firstResponseRemainingMs; }
    public long resolutionRemainingMs() { return resolutionRemainingMs; }
    public boolean breached() { return breached; }
}
