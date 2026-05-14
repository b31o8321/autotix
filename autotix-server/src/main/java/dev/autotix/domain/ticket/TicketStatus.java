package dev.autotix.domain.ticket;

/**
 * Ticket state machine.
 *
 * Transitions (enforced in {@link Ticket}):
 *  NEW                  → WAITING_ON_CUSTOMER (first outbound reply)
 *  NEW                  → OPEN (if no outbound ever, stays for reports)
 *  OPEN                 → WAITING_ON_CUSTOMER (outbound reply)
 *  WAITING_ON_CUSTOMER  → OPEN (inbound / customer reply)
 *  OPEN | WAITING_ON_*  → WAITING_ON_INTERNAL (manual escalation)
 *  OPEN | WAITING_ON_*  → SOLVED (solve())
 *  SOLVED               → OPEN (reopen(), within window)
 *  SOLVED               → CLOSED (auto after window OR permanentClose())
 *  any non-terminal     → CLOSED (permanentClose())
 *  any                  → SPAM (markSpam())
 *  CLOSED / SPAM are terminal — no further transitions allowed.
 *
 * NOTE: ASSIGNED is removed. Assignee is tracked via assigneeId field; status is
 * orthogonal. Old PENDING maps to WAITING_ON_CUSTOMER.
 */
public enum TicketStatus {
    /** Just received; no outbound reply yet. */
    NEW,
    /** Active conversation — awaiting agent/AI response. */
    OPEN,
    /** Agent/AI replied; waiting for customer response. */
    WAITING_ON_CUSTOMER,
    /** Escalated; waiting on internal team action. */
    WAITING_ON_INTERNAL,
    /** Tentatively resolved; customer can reopen within configured window. */
    SOLVED,
    /** Permanently closed. A new inbound spawns a new ticket. */
    CLOSED,
    /** Discarded as spam. Terminal. */
    SPAM
}
