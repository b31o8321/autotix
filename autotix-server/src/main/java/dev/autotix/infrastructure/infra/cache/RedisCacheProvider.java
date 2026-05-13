package dev.autotix.infrastructure.infra.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis cache implementation (stub).
 * Active when autotix.cache=redis.
 * TODO: implement with RedisTemplate + Jackson serialization in a future slice.
 */
@Component
@ConditionalOnProperty(name = "autotix.cache", havingValue = "redis")
public class RedisCacheProvider implements CacheProvider {

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        throw new UnsupportedOperationException("Redis cache not yet implemented");
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        throw new UnsupportedOperationException("Redis cache not yet implemented");
    }

    @Override
    public void evict(String key) {
        throw new UnsupportedOperationException("Redis cache not yet implemented");
    }

    @Override
    public <T> boolean putIfAbsent(String key, T value, Duration ttl) {
        // TODO: use Redis SET NX EX for true distributed atomicity
        throw new UnsupportedOperationException("Redis cache not yet implemented");
    }
}
