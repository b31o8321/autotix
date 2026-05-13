package dev.autotix.interfaces.inbox;

import dev.autotix.infrastructure.auth.CurrentUser;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * TODO: SSE stream for the Inbox live view.
 *
 *  GET /api/inbox/stream — text/event-stream
 *    Client (EventSource): subscribes once on Inbox page load; auto-reconnects.
 *    Server: pushes InboxEvent objects as `event: <kind>\ndata: <json>\n\n`
 *
 *  Auth: token must be authenticated (filter populates SecurityContext).
 *        Since EventSource cannot set headers, the frontend can pass token as
 *        ?token= query param — TODO: extend JwtAuthenticationFilter to accept that.
 *
 *  Heartbeat: send a comment line `: ping` every 25s to keep proxies alive.
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final InboxEventPublisher publisher;
    private final CurrentUser currentUser;

    public InboxController(InboxEventPublisher publisher, CurrentUser currentUser) {
        this.publisher = publisher;
        this.currentUser = currentUser;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        String userId = currentUser.id().value();
        return publisher.register(userId);
    }
}
