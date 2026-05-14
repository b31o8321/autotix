package dev.autotix.domain.ticket;

import dev.autotix.domain.channel.ChannelId;

import java.util.List;
import java.util.Optional;

/**
 * TODO: Repository port for Ticket aggregate.
 *  Implemented in infrastructure/persistence.
 *  Domain layer must not depend on JPA/MyBatis.
 */
public interface TicketRepository {

    /** TODO: persist new or updated ticket; return same id */
    TicketId save(Ticket ticket);

    /** TODO: find by internal id */
    Optional<Ticket> findById(TicketId id);

    /**
     * Lookup by channel + native external id.
     * Returns the MOST RECENT ticket matching the pair (ORDER BY created_at DESC LIMIT 1).
     * The unique constraint on (channel_id, external_native_id) has been dropped;
     * multiple tickets can share the same externalNativeId when a closed ticket
     * spawns a new one via {@link Ticket#spawnFromClosed}.
     */
    Optional<Ticket> findByChannelAndExternalId(ChannelId channelId, String externalNativeId);

    /**
     * Find tickets in SOLVED status where solvedAt is older than the given cutoff.
     * Used by AutoCloseSolvedTicketsScheduler.
     */
    List<Ticket> findSolvedBefore(java.time.Instant cutoff);

    /** TODO: paginated list for desk UI; filter params TBD (status, channel, assignee, ...) */
    List<Ticket> search(TicketSearchQuery query);

    /**
     * Find active tickets (not SOLVED/CLOSED/SPAM) where sla_breached = false AND
     * at least one SLA deadline has been missed:
     *   (firstResponseDueAt < now AND firstResponseAt IS NULL)
     *   OR
     *   (resolutionDueAt < now AND solvedAt IS NULL)
     * Used by SlaCheckerScheduler.
     */
    List<Ticket> findOverdue(java.time.Instant now);

    /**
     * Return the most recent tickets for a given customer, ordered by updated_at DESC, limited to `limit`.
     * Used by GetCustomerUseCase to populate recentTicketIds on the customer detail view.
     */
    List<Ticket> findByCustomerId(dev.autotix.domain.customer.CustomerId customerId, int limit);

    /**
     * Return the id of the last (most recently inserted) message for a ticket.
     * Used by Slice 11 to link uploaded attachments to the reply message.
     * Returns null if the ticket has no messages.
     */
    Long findLastMessageId(TicketId ticketId);

    /**
     * Return the IDs of all messages for a ticket, ordered by occurred_at ASC.
     * Used by DeskController to match attachments to message DTOs.
     */
    List<Long> findMessageIdsByTicketIdOrdered(TicketId ticketId);

    /**
     * E2E-B: Look up the internal ticket id for a message identified by its RFC-2822 Message-ID header.
     * Used by EmailInboxPoller to thread replies to existing tickets.
     * Returns null if not found.
     */
    TicketId findTicketIdByEmailMessageId(String emailMessageId);
}
