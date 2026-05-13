package dev.autotix.infrastructure.infra.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CaffeineCacheProviderTest {

    private CaffeineCacheProvider cache;

    @BeforeEach
    void setUp() {
        cache = new CaffeineCacheProvider();
    }

    @Test
    void put_then_get_returnsValue() {
        cache.put("key1", "hello", Duration.ofMinutes(5));
        Optional<String> result = cache.get("key1", String.class);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void get_after_ttl_elapsed_returnsEmpty() throws InterruptedException {
        cache.put("key2", "value", Duration.ofMillis(100));
        Thread.sleep(200); // wait for TTL to expire
        Optional<String> result = cache.get("key2", String.class);
        assertFalse(result.isPresent(), "Value should be gone after TTL");
    }

    @Test
    void evict_removesValue() {
        cache.put("key3", "data", Duration.ofMinutes(5));
        cache.evict("key3");
        assertFalse(cache.get("key3", String.class).isPresent(), "Evicted key should return empty");
    }

    @Test
    void putIfAbsent_firstCall_returnsTrue() {
        boolean result = cache.putIfAbsent("key4", "first", Duration.ofMinutes(5));
        assertTrue(result, "First putIfAbsent should return true");
    }

    @Test
    void putIfAbsent_secondCall_returnsFalse() {
        cache.putIfAbsent("key5", "first", Duration.ofMinutes(5));
        boolean result = cache.putIfAbsent("key5", "second", Duration.ofMinutes(5));
        assertFalse(result, "Second putIfAbsent on same key should return false");

        // Value should still be the original
        Optional<String> val = cache.get("key5", String.class);
        assertTrue(val.isPresent());
        assertEquals("first", val.get());
    }

    @Test
    void putIfAbsent_afterExpiry_returnsTrue() throws InterruptedException {
        cache.putIfAbsent("key6", "first", Duration.ofMillis(100));
        Thread.sleep(200); // wait for TTL
        boolean result = cache.putIfAbsent("key6", "second", Duration.ofMinutes(5));
        assertTrue(result, "putIfAbsent after expiry should return true");
        assertEquals("second", cache.get("key6", String.class).orElse(null));
    }

    @Test
    void differentTypes_storedCorrectly() {
        cache.put("int-key", 42, Duration.ofMinutes(5));
        Optional<Integer> result = cache.get("int-key", Integer.class);
        assertTrue(result.isPresent());
        assertEquals(42, result.get().intValue());
    }
}
