package dev.autotix.infrastructure.infra.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default JVM-local lock implementation.
 * Uses ConcurrentHashMap with AtomicBoolean flags to track held keys.
 * Non-reentrant: a thread that already holds a key cannot reacquire it.
 *
 * TTL is enforced via a ScheduledExecutorService that force-releases the lock
 * if the holder hasn't released it within the ttl window.
 */
@Component
@ConditionalOnProperty(name = "autotix.lock", havingValue = "memory", matchIfMissing = true)
public class InMemoryLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLockProvider.class);

    /**
     * Keys currently held. A key is "locked" when present and true.
     * We use putIfAbsent-style logic on ConcurrentHashMap to achieve non-reentrant CAS.
     */
    private final ConcurrentHashMap<String, AtomicBoolean> locks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "autotix-lock-scheduler");
                t.setDaemon(true);
                return t;
            });

    @Override
    public LockHandle tryAcquire(String key, Duration ttl) {
        // Create a new AtomicBoolean marker for this key (false = released)
        AtomicBoolean marker = new AtomicBoolean(true);
        AtomicBoolean existing = locks.putIfAbsent(key, marker);
        if (existing != null) {
            // Key is already present — check if the holder has released it
            if (!existing.compareAndSet(false, true)) {
                // Lock is held (was true, CAS to true failed because it's already true)
                return null;
            }
            // Lock was released (was false), we've now re-taken it
            marker = existing;
        }

        final AtomicBoolean heldMarker = marker;
        ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];

        Runnable forceRelease = () -> {
            log.warn("Lock TTL expired for key='{}'; force-releasing", key);
            heldMarker.set(false);
            locks.remove(key, heldMarker);
        };
        futureHolder[0] = scheduler.schedule(forceRelease, ttl.toMillis(), TimeUnit.MILLISECONDS);

        return new LockHandle() {
            private volatile boolean released = false;

            @Override
            public void close() {
                if (released) {
                    return;
                }
                released = true;
                futureHolder[0].cancel(false);
                heldMarker.set(false);
                locks.remove(key, heldMarker);
            }
        };
    }

    @Override
    public LockHandle acquire(String key, Duration ttl, Duration waitAtMost) {
        long deadlineNanos = System.nanoTime() + waitAtMost.toNanos();

        while (true) {
            LockHandle handle = tryAcquire(key, ttl);
            if (handle != null) {
                return handle;
            }
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            try {
                Thread.sleep(Math.min(50, TimeUnit.NANOSECONDS.toMillis(remaining) + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
