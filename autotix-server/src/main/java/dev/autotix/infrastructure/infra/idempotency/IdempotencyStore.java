package dev.autotix.infrastructure.infra.idempotency;

import java.time.Duration;

/**
 * TODO: Idempotency check for incoming webhook events.
 *  Key shape: "{platform}:{channelId}:{externalTicketId}:{occurredAt}".
 *  Typical default: backed by CacheProvider with ~24h TTL.
 */
public interface IdempotencyStore {

    /** TODO: atomic check-and-set; returns true if THIS call is the first one. */
    boolean tryMark(String key, Duration ttl);
}
