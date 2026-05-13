package dev.autotix.infrastructure.infra.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * TODO: Redis cache implementation.
 *  Active when autotix.cache=redis.
 */
@Component
@ConditionalOnProperty(name = "autotix.cache", havingValue = "redis")
public class RedisCacheProvider implements CacheProvider {

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        // TODO: RedisTemplate get + jackson deserialize
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        // TODO: SET key value EX ttl
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void evict(String key) {
        // TODO: DEL key
        throw new UnsupportedOperationException("TODO");
    }
}
