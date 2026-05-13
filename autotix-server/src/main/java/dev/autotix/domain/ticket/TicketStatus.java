package dev.autotix.domain.ticket;

/**
 * TODO: Ticket state machine. Define legal transitions in {@link Ticket}.
 *  OPEN     -> PENDING (AI replied, waiting for customer)
 *  OPEN     -> ASSIGNED (handed to human agent)
 *  PENDING  -> OPEN (new inbound message)
 *  PENDING  -> CLOSED
 *  ASSIGNED -> CLOSED
 *  any      -> CLOSED
 */
public enum TicketStatus {
    OPEN,
    PENDING,
    ASSIGNED,
    CLOSED
}
