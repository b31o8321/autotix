package dev.autotix.infrastructure.infra.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * TODO: Default JVM-local lock implementation (ConcurrentHashMap + ReentrantLock).
 *  Active when autotix.lock=memory (default).
 */
@Component
@ConditionalOnProperty(name = "autotix.lock", havingValue = "memory", matchIfMissing = true)
public class InMemoryLockProvider implements LockProvider {

    @Override
    public LockHandle tryAcquire(String key, Duration ttl) {
        // TODO: ConcurrentHashMap<String, ReentrantLock>; honor ttl via scheduled release
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LockHandle acquire(String key, Duration ttl, Duration waitAtMost) {
        // TODO: blocking version
        throw new UnsupportedOperationException("TODO");
    }
}
