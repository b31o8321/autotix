package dev.autotix.infrastructure.infra.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * TODO: Caffeine in-memory cache implementation.
 *  Default; active when autotix.cache=caffeine.
 */
@Component
@ConditionalOnProperty(name = "autotix.cache", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineCacheProvider implements CacheProvider {

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        // TODO: Caffeine.getIfPresent + safe cast
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        // TODO: per-key TTL — use ExpiringMap or VarExpiration policy
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void evict(String key) {
        // TODO: invalidate
        throw new UnsupportedOperationException("TODO");
    }
}
