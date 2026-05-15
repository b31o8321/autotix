package dev.autotix.infrastructure.livechat;

import com.alibaba.fastjson.JSON;
import dev.autotix.domain.ticket.TicketId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe registry mapping ticketId -> set of open WebSocket sessions.
 * Multiple sessions per ticket are supported (e.g. same visitor opens two tabs).
 */
@Component
public class LiveChatSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(LiveChatSessionRegistry.class);

    /** key = ticketId.value(), value = set of open sessions for that ticket */
    private final Map<String, Set<WebSocketSession>> sessionsByTicket = new ConcurrentHashMap<>();

    /** Reverse map: sessionId -> ticketId.value(), for fast unregister */
    private final Map<String, String> ticketBySession = new ConcurrentHashMap<>();

    public void register(TicketId ticketId, WebSocketSession session) {
        String tid = ticketId.value();
        sessionsByTicket.computeIfAbsent(tid, k -> new CopyOnWriteArraySet<>()).add(session);
        ticketBySession.put(session.getId(), tid);
        log.debug("[LiveChat] registered session={} for ticket={}", session.getId(), tid);
    }

    public void unregister(WebSocketSession session) {
        String tid = ticketBySession.remove(session.getId());
        if (tid != null) {
            Set<WebSocketSession> set = sessionsByTicket.get(tid);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    sessionsByTicket.remove(tid);
                }
            }
            log.debug("[LiveChat] unregistered session={} from ticket={}", session.getId(), tid);
        }
    }

    /**
     * Push a raw JSON string to all open sessions for the given ticket.
     * Dead sessions are closed and removed.
     */
    public void pushToTicket(TicketId ticketId, String jsonPayload) {
        Set<WebSocketSession> sessions = sessionsByTicket.get(ticketId.value());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        List<WebSocketSession> dead = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                dead.add(session);
                continue;
            }
            try {
                session.sendMessage(new TextMessage(jsonPayload));
            } catch (IOException e) {
                log.warn("[LiveChat] failed to push to session={}: {}", session.getId(), e.getMessage());
                dead.add(session);
            }
        }
        for (WebSocketSession d : dead) {
            sessions.remove(d);
            ticketBySession.remove(d.getId());
            try { d.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Push a status-change frame to all sessions for the ticket.
     */
    public void pushStatus(TicketId ticketId, String status) {
        Map<String, Object> frame = new java.util.LinkedHashMap<>();
        frame.put("type", "status");
        frame.put("status", status);
        pushToTicket(ticketId, JSON.toJSONString(frame));
    }

    /** Package-private: how many tickets have active sessions (used in tests). */
    int activeTicketCount() {
        return sessionsByTicket.size();
    }

    /** Package-private: sessions for a given ticket (used in tests). */
    Set<WebSocketSession> sessionsForTicket(TicketId ticketId) {
        return sessionsByTicket.getOrDefault(ticketId.value(), new CopyOnWriteArraySet<>());
    }
}
