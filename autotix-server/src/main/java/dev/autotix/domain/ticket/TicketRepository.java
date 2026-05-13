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

    /** TODO: lookup for idempotency — by channel + native external id */
    Optional<Ticket> findByChannelAndExternalId(ChannelId channelId, String externalNativeId);

    /** TODO: paginated list for desk UI; filter params TBD (status, channel, assignee, ...) */
    List<Ticket> search(TicketSearchQuery query);
}
