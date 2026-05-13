package dev.autotix.infrastructure.inbox;

import dev.autotix.domain.event.InboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit tests for InboxEventPublisher fan-out logic.
 * Uses package-private addEmitterForTest() helper to inject capturing emitters.
 */
class InboxEventPublisherTest {

    /** Captures all SseEmitter.send() calls for inspection in tests. */
    static class CapturingEmitter extends SseEmitter {
        final java.util.List<Object> sent = new CopyOnWriteArrayList<>();
        boolean throwOnSend = false;

        CapturingEmitter() { super(0L); }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) throws IOException {
            if (throwOnSend) {
                throw new IOException("simulated send failure");
            }
            sent.add(builder);
        }
    }

    private InboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new InboxEventPublisher();
    }

    @Test
    void register_addsEmitter_publish_deliversToIt() throws Exception {
        CapturingEmitter emitter = new CapturingEmitter();
        publisher.addEmitterForTest("user-1", emitter);

        InboxEvent event = new InboxEvent(InboxEvent.Kind.TICKET_CREATED,
                "t1", "ch1", "New ticket", Instant.now());
        publisher.publish(event);

        // The capturing emitter should have received the event
        assertFalse(emitter.sent.isEmpty(), "Emitter should receive published event");
        assertEquals(1, publisher.connectedEmitterCount());
    }

    @Test
    void publishTo_onlyDeliversToTargetUser() throws Exception {
        CapturingEmitter emitter1 = new CapturingEmitter();
        CapturingEmitter emitter2 = new CapturingEmitter();
        publisher.addEmitterForTest("user-A", emitter1);
        publisher.addEmitterForTest("user-B", emitter2);

        InboxEvent event = new InboxEvent(InboxEvent.Kind.AI_REPLIED,
                "t2", "ch1", "AI replied", Instant.now());
        publisher.publishTo("user-A", event);

        assertFalse(emitter1.sent.isEmpty(), "user-A emitter should receive the event");
        assertTrue(emitter2.sent.isEmpty(), "user-B emitter should NOT receive the event");
    }

    @Test
    void failedSend_removesEmitter() throws Exception {
        CapturingEmitter failing = new CapturingEmitter();
        failing.throwOnSend = true;
        publisher.addEmitterForTest("user-fail", failing);

        assertEquals(1, publisher.connectedEmitterCount());

        InboxEvent event = new InboxEvent(InboxEvent.Kind.AGENT_REPLIED,
                "t3", "ch1", "Agent replied", Instant.now());
        publisher.publish(event);

        // After failed send, emitter should be removed
        assertEquals(0, publisher.connectedEmitterCount(),
                "Failed emitter should be removed after IOException");
    }

    @Test
    void heartbeat_deliversPingCommentToAll() throws Exception {
        CapturingEmitter emitter1 = new CapturingEmitter();
        CapturingEmitter emitter2 = new CapturingEmitter();
        publisher.addEmitterForTest("u1", emitter1);
        publisher.addEmitterForTest("u2", emitter2);

        publisher.heartbeat();

        assertFalse(emitter1.sent.isEmpty(), "u1 emitter should receive heartbeat ping");
        assertFalse(emitter2.sent.isEmpty(), "u2 emitter should receive heartbeat ping");
    }
}
