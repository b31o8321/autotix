package dev.autotix.infrastructure.infra.lock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * TODO: Redis-based distributed lock (Redisson or raw SET NX PX).
 *  Active when autotix.lock=redis.
 */
@Component
@ConditionalOnProperty(name = "autotix.lock", havingValue = "redis")
public class RedisLockProvider implements LockProvider {

    @Override
    public LockHandle tryAcquire(String key, Duration ttl) {
        // TODO: SET key value NX PX <ttl>
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public LockHandle acquire(String key, Duration ttl, Duration waitAtMost) {
        // TODO: poll with small backoff until deadline
        throw new UnsupportedOperationException("TODO");
    }
}
