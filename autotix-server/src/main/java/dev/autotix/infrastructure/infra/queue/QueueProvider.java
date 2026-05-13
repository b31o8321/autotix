package dev.autotix.infrastructure.infra.queue;

import java.util.function.Consumer;

/**
 * TODO: Async work queue abstraction.
 *  Used by ProcessWebhookUseCase to enqueue AI dispatch jobs.
 *  Default: in-memory (executor + queue). Replaceable: Kafka / Rabbit / Rocket.
 */
public interface QueueProvider {

    /** TODO: publish a job to a named topic; payload is JSON-serialized. */
    void publish(String topic, String payload);

    /** TODO: register a consumer for a topic; invoked async on each message. */
    void subscribe(String topic, Consumer<String> handler);
}
