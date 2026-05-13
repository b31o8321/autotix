package dev.autotix.infrastructure.infra.idempotency;

import dev.autotix.infrastructure.infra.cache.CacheProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default idempotency store delegating to CacheProvider.putIfAbsent.
 *
 * Atomicity: CaffeineCacheProvider uses striped ReentrantLocks (JVM-local).
 * For distributed mode, switch to RedisCacheProvider which should implement SET NX EX.
 */
@Component
public class CacheBackedIdempotencyStore implements IdempotencyStore {

    private static final String MARKER = "1";

    private final CacheProvider cache;

    public CacheBackedIdempotencyStore(CacheProvider cache) {
        this.cache = cache;
    }

    @Override
    public boolean tryMark(String key, Duration ttl) {
        return cache.putIfAbsent(key, MARKER, ttl);
    }
}
