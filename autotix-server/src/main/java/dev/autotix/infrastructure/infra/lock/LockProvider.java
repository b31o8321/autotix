package dev.autotix.infrastructure.infra.lock;

import java.time.Duration;

/**
 * TODO: Distributed lock abstraction.
 *  Default: InMemoryLockProvider (JVM-local).
 *  Switch to Redis/Zookeeper via autotix.lock=redis.
 */
public interface LockProvider {

    /** TODO: try-acquire with TTL; returns null if not acquired. */
    LockHandle tryAcquire(String key, Duration ttl);

    /** TODO: blocking acquire with overall wait time. */
    LockHandle acquire(String key, Duration ttl, Duration waitAtMost);

    interface LockHandle extends AutoCloseable {
        @Override void close();   // release; suppress checked exception
    }
}
