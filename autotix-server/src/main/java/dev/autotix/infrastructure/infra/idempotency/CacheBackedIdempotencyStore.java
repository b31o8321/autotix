package dev.autotix.infrastructure.infra.idempotency;

import dev.autotix.infrastructure.infra.cache.CacheProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * TODO: Default idempotency store delegating to CacheProvider.
 *  Implementation note: need atomic putIfAbsent semantics — extend CacheProvider with that,
 *  or use Redis SET NX EX when running in distributed mode.
 */
@Component
public class CacheBackedIdempotencyStore implements IdempotencyStore {

    private final CacheProvider cache;

    public CacheBackedIdempotencyStore(CacheProvider cache) {
        this.cache = cache;
    }

    @Override
    public boolean tryMark(String key, Duration ttl) {
        // TODO: atomic putIfAbsent semantics
        throw new UnsupportedOperationException("TODO");
    }
}
