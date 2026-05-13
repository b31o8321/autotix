package dev.autotix.infrastructure.infra.queue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryQueueProviderTest {

    private InMemoryQueueProvider queueProvider;

    @BeforeEach
    void setUp() {
        queueProvider = new InMemoryQueueProvider();
    }

    @Test
    void subscribe_then_publish_invokesHandlerWithPayload() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        queueProvider.subscribe("test.topic", payload -> {
            received.set(payload);
            latch.countDown();
        });

        queueProvider.publish("test.topic", "hello-world");

        boolean invoked = latch.await(2, TimeUnit.SECONDS);
        assertTrue(invoked, "Handler should be invoked within 2s");
        assertEquals("hello-world", received.get());
    }

    @Test
    void handlerException_doesNotKillWorker_secondMessageStillDelivered() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        queueProvider.subscribe("resilient.topic", payload -> {
            if ("bad".equals(payload)) {
                throw new RuntimeException("Simulated handler failure");
            }
            successCount.incrementAndGet();
            latch.countDown();
        });

        // First publish — will throw
        queueProvider.publish("resilient.topic", "bad");
        // Second publish — should still be processed
        queueProvider.publish("resilient.topic", "good");

        boolean delivered = latch.await(2, TimeUnit.SECONDS);
        assertTrue(delivered, "Second message should still be delivered after first handler throws");
        assertEquals(1, successCount.get());
    }

    @Test
    void multipleTopicsAreIndependent() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> topicA = new AtomicReference<>();
        AtomicReference<String> topicB = new AtomicReference<>();

        queueProvider.subscribe("topic.a", payload -> {
            topicA.set(payload);
            latch.countDown();
        });
        queueProvider.subscribe("topic.b", payload -> {
            topicB.set(payload);
            latch.countDown();
        });

        queueProvider.publish("topic.a", "msg-a");
        queueProvider.publish("topic.b", "msg-b");

        boolean done = latch.await(2, TimeUnit.SECONDS);
        assertTrue(done, "Both handlers should fire");
        assertEquals("msg-a", topicA.get());
        assertEquals("msg-b", topicB.get());
    }
}
