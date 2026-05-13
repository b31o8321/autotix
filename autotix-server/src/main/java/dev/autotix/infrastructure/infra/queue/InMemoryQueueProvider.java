package dev.autotix.infrastructure.infra.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * TODO: In-process queue (ExecutorService + per-topic BlockingQueue).
 *  Active when autotix.queue=memory.
 *  WARNING: messages lost on restart — fine for dev / single-node small load.
 */
@Component
@ConditionalOnProperty(name = "autotix.queue", havingValue = "memory", matchIfMissing = true)
public class InMemoryQueueProvider implements QueueProvider {

    @Override
    public void publish(String topic, String payload) {
        // TODO: enqueue to per-topic queue; spawn worker if none
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void subscribe(String topic, Consumer<String> handler) {
        // TODO: register handler; pull loop on executor
        throw new UnsupportedOperationException("TODO");
    }
}
