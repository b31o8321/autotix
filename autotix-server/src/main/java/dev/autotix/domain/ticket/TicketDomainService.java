package dev.autotix.domain.ticket;

/**
 * Domain service for cross-aggregate behaviors that don't naturally live
 * on the Ticket aggregate (e.g. merging duplicate tickets, bulk reassign).
 * Keep stateless; no Spring annotations here (domain layer is framework-free).
 */
public class TicketDomainService {

    /**
     * Merge two tickets (e.g. duplicate detection across channels).
     * Not used in Slice 2 — deferred.
     */
    public Ticket merge(Ticket primary, Ticket duplicate) {
        // TODO: implement ticket merge in a future slice
        throw new UnsupportedOperationException("merge not yet implemented");
    }

    /**
     * Decide whether ticket qualifies for AI auto-reply.
     * Ticket must be NEW or OPEN and have at least one message.
     * WAITING_ON_CUSTOMER means we already replied; skip re-dispatching.
     */
    public boolean shouldAutoReply(Ticket ticket) {
        return (ticket.status() == TicketStatus.NEW || ticket.status() == TicketStatus.OPEN)
                && !ticket.messages().isEmpty();
    }
}
