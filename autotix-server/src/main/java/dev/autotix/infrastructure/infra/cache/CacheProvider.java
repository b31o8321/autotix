package dev.autotix.infrastructure.infra.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * TODO: Key-value cache abstraction (used for AI prompt cache, access_token cache, etc.).
 */
public interface CacheProvider {
    <T> Optional<T> get(String key, Class<T> type);
    <T> void put(String key, T value, Duration ttl);
    void evict(String key);
}
