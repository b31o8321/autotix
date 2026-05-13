package dev.autotix.infrastructure.infra.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryLockProviderTest {

    private InMemoryLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        lockProvider = new InMemoryLockProvider();
    }

    @Test
    void tryAcquire_freeLock_returnsHandle_and_close_releases() {
        LockProvider.LockHandle handle = lockProvider.tryAcquire("key1", Duration.ofSeconds(30));
        assertNotNull(handle, "Should acquire a free lock");

        // Second acquire while held should fail
        LockProvider.LockHandle handle2 = lockProvider.tryAcquire("key1", Duration.ofSeconds(30));
        assertNull(handle2, "Should not acquire already-held lock");

        // Release
        handle.close();

        // Now it should be acquirable again
        LockProvider.LockHandle handle3 = lockProvider.tryAcquire("key1", Duration.ofSeconds(30));
        assertNotNull(handle3, "Should reacquire after release");
        handle3.close();
    }

    @Test
    void tryAcquire_heldLock_returnsNull() {
        LockProvider.LockHandle handle = lockProvider.tryAcquire("key2", Duration.ofSeconds(30));
        assertNotNull(handle);

        LockProvider.LockHandle handle2 = lockProvider.tryAcquire("key2", Duration.ofSeconds(30));
        assertNull(handle2, "tryAcquire on held lock must return null");

        handle.close();
    }

    @Test
    void acquire_blocking_returnsWithinWaitTime() {
        // Start with a free lock — should return immediately
        LockProvider.LockHandle handle = lockProvider.acquire("key3",
                Duration.ofSeconds(30), Duration.ofSeconds(3));
        assertNotNull(handle, "Blocking acquire on free lock should succeed within wait");
        handle.close();
    }

    @Test
    void acquire_blocking_returnsNullAfterWaitExpires() throws InterruptedException {
        // Hold the lock in current thread
        LockProvider.LockHandle held = lockProvider.tryAcquire("key4", Duration.ofSeconds(30));
        assertNotNull(held);

        // Try to acquire with a short wait — should time out
        long start = System.currentTimeMillis();
        LockProvider.LockHandle handle = lockProvider.acquire("key4",
                Duration.ofSeconds(30), Duration.ofMillis(200));
        long elapsed = System.currentTimeMillis() - start;

        assertNull(handle, "Blocking acquire should return null after wait expires");
        assertTrue(elapsed >= 150, "Should have waited at least ~200ms");

        held.close();
    }

    @Test
    void differentKeys_independentLocks() {
        LockProvider.LockHandle h1 = lockProvider.tryAcquire("key-a", Duration.ofSeconds(30));
        LockProvider.LockHandle h2 = lockProvider.tryAcquire("key-b", Duration.ofSeconds(30));

        assertNotNull(h1, "key-a should be acquirable");
        assertNotNull(h2, "key-b should be acquirable independently");

        h1.close();
        h2.close();
    }
}
