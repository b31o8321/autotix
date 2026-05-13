package dev.autotix.infrastructure.infra.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Key-value cache abstraction (used for AI prompt cache, access_token cache, idempotency, etc.).
 */
public interface CacheProvider {
    <T> Optional<T> get(String key, Class<T> type);
    <T> void put(String key, T value, Duration ttl);
    void evict(String key);

    /**
     * Atomic check-and-set: stores the value only if the key is not already present (or expired).
     * Returns true if the value was stored (first call), false if it was already present.
     *
     * NOTE: The default in-memory implementation uses a synchronized block for atomicity.
     * Redis implementation should use SET NX EX for true distributed atomicity.
     */
    <T> boolean putIfAbsent(String key, T value, Duration ttl);
}
