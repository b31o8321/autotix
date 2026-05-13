package dev.autotix.infrastructure.infra.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * In-process queue backed by per-topic LinkedBlockingQueue + single-thread worker.
 * Active when autotix.queue=memory (default).
 *
 * WARNING: messages are lost on restart — acceptable for dev / single-node.
 * Handler exceptions are caught and logged so the worker thread stays alive.
 */
@Component
@ConditionalOnProperty(name = "autotix.queue", havingValue = "memory", matchIfMissing = true)
public class InMemoryQueueProvider implements QueueProvider {

    private static final Logger log = LoggerFactory.getLogger(InMemoryQueueProvider.class);

    private final ConcurrentHashMap<String, LinkedBlockingQueue<String>> queues =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Consumer<String>> handlers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ExecutorService> workers =
            new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, String payload) {
        getOrCreateQueue(topic).add(payload);
    }

    @Override
    public void subscribe(String topic, Consumer<String> handler) {
        handlers.put(topic, handler);
        getOrCreateQueue(topic); // ensure queue + worker exist
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private LinkedBlockingQueue<String> getOrCreateQueue(String topic) {
        return queues.computeIfAbsent(topic, t -> {
            LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
            ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "autotix-queue-" + t);
                thread.setDaemon(true);
                return thread;
            });
            workers.put(t, worker);
            worker.submit(() -> runWorker(t, queue));
            return queue;
        });
    }

    private void runWorker(String topic, LinkedBlockingQueue<String> queue) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String payload = queue.take(); // blocks until item available
                Consumer<String> handler = handlers.get(topic);
                if (handler != null) {
                    try {
                        handler.accept(payload);
                    } catch (Exception e) {
                        log.error("Handler exception for topic='{}', payload='{}': {}",
                                topic, payload, e.getMessage(), e);
                        // Continue — don't kill the worker
                    }
                } else {
                    log.debug("No handler registered for topic='{}', dropping payload", topic);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Queue worker for topic='{}' interrupted, stopping", topic);
                break;
            } catch (Exception e) {
                log.error("Unexpected error in queue worker for topic='{}'", topic, e);
                // Continue running
            }
        }
    }
}
