package dev.autotix.infrastructure.infra.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * TODO: Kafka-based queue for distributed deployments.
 *  Active when autotix.queue=kafka.
 *  Requires spring-kafka dep (add to pom when this profile is used).
 */
@Component
@ConditionalOnProperty(name = "autotix.queue", havingValue = "kafka")
public class KafkaQueueProvider implements QueueProvider {

    @Override
    public void publish(String topic, String payload) {
        // TODO: KafkaTemplate.send(topic, payload)
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void subscribe(String topic, Consumer<String> handler) {
        // TODO: dynamic @KafkaListener registration or programmatic ConcurrentMessageListenerContainer
        throw new UnsupportedOperationException("TODO");
    }
}
