package dev.autotix.infrastructure.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Caffeine in-memory cache implementation.
 * Default; active when autotix.cache=caffeine.
 *
 * Per-key TTL is implemented by storing values in a simple ConcurrentHashMap with an expiry
 * timestamp. Caffeine's maximum size eviction provides a cap on memory usage.
 *
 * putIfAbsent atomicity: we use a striped lock (key-hash-based ReentrantLock array) to
 * serialize concurrent check-and-set on the same key. Note: this is JVM-local only;
 * distributed putIfAbsent requires Redis SET NX.
 */
@Component
@ConditionalOnProperty(name = "autotix.cache", havingValue = "caffeine", matchIfMissing = true)
public class CaffeineCacheProvider implements CacheProvider {

    private static final int STRIPE_COUNT = 64;

    /** Simple holder: the value + absolute expiry epoch millis. */
    private static final class Entry {
        final Object value;
        final long expiresAtMillis;

        Entry(Object value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    // We use Caffeine for memory bounding + background eviction, but we track
    // per-key TTL ourselves via Entry.expiresAtMillis.
    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();

    // Striped locks for putIfAbsent atomicity (JVM-local)
    private final ReentrantLock[] stripes = new ReentrantLock[STRIPE_COUNT];

    public CaffeineCacheProvider() {
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripes[i] = new ReentrantLock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.isExpired()) {
            cache.invalidate(key);
            return Optional.empty();
        }
        return Optional.ofNullable(type.cast(entry.value));
    }

    @Override
    public <T> void put(String key, T value, Duration ttl) {
        long expiresAt = Instant.now().plusMillis(ttl.toMillis()).toEpochMilli();
        cache.put(key, new Entry(value, expiresAt));
    }

    @Override
    public void evict(String key) {
        cache.invalidate(key);
    }

    @Override
    public <T> boolean putIfAbsent(String key, T value, Duration ttl) {
        ReentrantLock stripe = stripes[Math.abs(key.hashCode() % STRIPE_COUNT)];
        stripe.lock();
        try {
            Entry existing = cache.getIfPresent(key);
            if (existing != null && !existing.isExpired()) {
                return false; // already present
            }
            // Not present or expired — store it
            long expiresAt = Instant.now().plusMillis(ttl.toMillis()).toEpochMilli();
            cache.put(key, new Entry(value, expiresAt));
            return true;
        } finally {
            stripe.unlock();
        }
    }
}
