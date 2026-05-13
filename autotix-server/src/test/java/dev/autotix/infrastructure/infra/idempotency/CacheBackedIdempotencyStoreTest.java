package dev.autotix.infrastructure.infra.idempotency;

import dev.autotix.infrastructure.infra.cache.CaffeineCacheProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CacheBackedIdempotencyStoreTest {

    private CacheBackedIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new CacheBackedIdempotencyStore(new CaffeineCacheProvider());
    }

    @Test
    void tryMark_firstCall_returnsTrue() {
        boolean result = store.tryMark("event:channel1:ticket1:1234567890", Duration.ofHours(24));
        assertTrue(result, "First tryMark should return true");
    }

    @Test
    void tryMark_secondCall_sameKey_returnsFalse() {
        String key = "event:channel2:ticket2:1234567890";
        boolean first = store.tryMark(key, Duration.ofHours(24));
        boolean second = store.tryMark(key, Duration.ofHours(24));

        assertTrue(first, "First call should return true");
        assertFalse(second, "Second call with same key should return false");
    }

    @Test
    void tryMark_afterTtlElapsed_returnsTrue() throws InterruptedException {
        String key = "event:channel3:ticket3:1234567890";
        boolean first = store.tryMark(key, Duration.ofMillis(100));
        assertTrue(first);

        Thread.sleep(200); // wait for TTL

        boolean afterExpiry = store.tryMark(key, Duration.ofHours(24));
        assertTrue(afterExpiry, "After TTL expiry, tryMark should return true again");
    }

    @Test
    void differentKeys_independentIdempotency() {
        boolean r1 = store.tryMark("key-a", Duration.ofHours(1));
        boolean r2 = store.tryMark("key-b", Duration.ofHours(1));

        assertTrue(r1, "key-a should be marked");
        assertTrue(r2, "key-b should be independently marked");
    }
}
