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
}
