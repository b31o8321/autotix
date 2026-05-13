package dev.autotix.interfaces.inbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * TODO: SSE smoke test.
 *  - GET /api/inbox/stream returns 200 with Content-Type: text/event-stream
 *  - After connecting, calling InboxEventPublisher.publish(event) -> client receives event
 *  - Tip: use WebClient with Flux<ServerSentEvent<String>> for the client side
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InboxControllerTest {

    @Test
    void stream_receivesPublishedEvent() {
        // TODO
    }
}
