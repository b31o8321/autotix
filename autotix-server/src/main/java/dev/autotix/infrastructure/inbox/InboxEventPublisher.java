package dev.autotix.infrastructure.inbox;

import dev.autotix.domain.event.InboxEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TODO: Fan-out hub for SSE Inbox streams.
 *  Application layer calls publish(event); each connected SseEmitter receives it.
 *
 *  Threading:
 *    - emitters per user keyed by userId (one user may have several tabs)
 *    - publish() iterates and emitter.send(); on IOException, remove the emitter
 *
 *  Distributed mode (autotix.queue=kafka):
 *    - publish also writes to "inbox.events" topic
 *    - a Kafka consumer in this class re-fans to local emitters
 *      (so events from another node reach this node's connected agents)
 */
@Component
public class InboxEventPublisher {

    /** key = userId, value = list of active emitters for that user */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** TODO: called by SSE controller when a new client connects. */
    public SseEmitter register(String userId) {
        // TODO:
        //   - new SseEmitter(timeout = 0 / forever)
        //   - emitter.onCompletion/onTimeout/onError -> remove from map
        //   - emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter)
        //   - return emitter
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: publish event to all currently connected agents (with optional filtering). */
    public void publish(InboxEvent event) {
        // TODO:
        //   - iterate all emitters
        //   - for each, emitter.send(SseEmitter.event().name(event.kind.name()).data(event))
        //   - on IOException, mark emitter for removal
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: targeted publish (e.g. only to assignee). */
    public void publishTo(String userId, InboxEvent event) {
        // TODO: lookup userId in emitters map; send to each
        throw new UnsupportedOperationException("TODO");
    }

    @SuppressWarnings("unused")
    private void unused() throws IOException {
        new CopyOnWriteArrayList<>();
    }
}
