package dev.autotix.infrastructure.inbox;

import com.alibaba.fastjson.JSON;
import dev.autotix.domain.event.InboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fan-out hub for SSE Inbox streams.
 * Application layer calls publish(event); each connected SseEmitter receives it.
 *
 * Threading:
 *   - emitters per user keyed by userId (one user may have several tabs)
 *   - publish() iterates and emitter.send(); on IOException, removes the emitter
 *   - CopyOnWriteArrayList allows safe iteration while removals happen on send failure
 *
 * Heartbeat: every 25 seconds a comment ping is sent to all emitters to keep
 * proxies and load balancers from dropping idle connections.
 */
@Component
public class InboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InboxEventPublisher.class);

    /** key = userId, value = list of active emitters for that user */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Called by SSE controller when a new client connects.
     * Creates an emitter with no timeout (0L), registers lifecycle callbacks,
     * sends an initial "ready" event, and adds it to the emitter map.
     */
    public SseEmitter register(String userId) {
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Send initial ready event so client knows the stream is open
        try {
            emitter.send(SseEmitter.event().name("ready").data("connected", MediaType.TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("Failed to send ready event to userId={}: {}", userId, e.getMessage());
            removeEmitter(userId, emitter);
        }

        log.debug("SSE emitter registered for userId={}, total={}", userId, connectedEmitterCount());
        return emitter;
    }

    /**
     * Publish event to ALL currently connected agents across ALL users.
     */
    public void publish(InboxEvent event) {
        String json = JSON.toJSONString(event);
        List<SseEmitter> toRemove = new ArrayList<>();

        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event()
                            .name(event.kind.name())
                            .data(json, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    log.debug("Failed to send to emitter for userId={}, marking for removal", entry.getKey());
                    toRemove.add(emitter);
                }
            }
            // Remove failed emitters after iteration
            entry.getValue().removeAll(toRemove);
            toRemove.clear();
        }
    }

    /**
     * Targeted publish — only to a specific user's emitters.
     */
    public void publishTo(String userId, InboxEvent event) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        String json = JSON.toJSONString(event);
        List<SseEmitter> toRemove = new ArrayList<>();

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.kind.name())
                        .data(json, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.debug("Failed to send to emitter for userId={}, marking for removal", userId);
                toRemove.add(emitter);
            }
        }
        userEmitters.removeAll(toRemove);
    }

    /**
     * Scheduled heartbeat: sends a comment ping every 25 seconds to all emitters
     * to prevent proxies and load balancers from closing idle SSE connections.
     */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        List<SseEmitter> toRemove = new ArrayList<>();

        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    log.debug("Heartbeat failed for userId={}, removing emitter", entry.getKey());
                    toRemove.add(emitter);
                }
            }
            entry.getValue().removeAll(toRemove);
            toRemove.clear();
        }
    }

    // -----------------------------------------------------------------------
    // Package-private helpers
    // -----------------------------------------------------------------------

    /**
     * Package-private helper for tests: inject a pre-built emitter without the
     * real SseEmitter lifecycle callbacks (which would call complete() on test instances).
     */
    void addEmitterForTest(String userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    /** Returns the total number of active emitters across all users. Used in tests. */
    int connectedEmitterCount() {
        int count = 0;
        for (List<SseEmitter> list : emitters.values()) {
            count += list.size();
        }
        return count;
    }

    private void removeEmitter(String userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
