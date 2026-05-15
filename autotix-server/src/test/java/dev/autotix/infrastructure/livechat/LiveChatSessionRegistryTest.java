package dev.autotix.infrastructure.livechat;

import dev.autotix.domain.ticket.TicketId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure JUnit + Mockito tests for LiveChatSessionRegistry.
 * Verifies: register, push, dead-session cleanup, unregister.
 */
class LiveChatSessionRegistryTest {

    private LiveChatSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LiveChatSessionRegistry();
    }

    private WebSocketSession openSession(String id) throws IOException {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private WebSocketSession closedSession(String id) {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn(id);
        when(s.isOpen()).thenReturn(false);
        return s;
    }

    @Test
    void register_and_push() throws Exception {
        TicketId tid = new TicketId("t1");
        WebSocketSession session = openSession("s1");

        registry.register(tid, session);
        assertEquals(1, registry.activeTicketCount());

        registry.pushToTicket(tid, "{\"type\":\"message\"}");
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void unregister_removes_session() throws Exception {
        TicketId tid = new TicketId("t2");
        WebSocketSession session = openSession("s2");

        registry.register(tid, session);
        assertEquals(1, registry.activeTicketCount());

        registry.unregister(session);
        assertEquals(0, registry.activeTicketCount());

        // Push to now-empty ticket should not throw
        registry.pushToTicket(tid, "{}");
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void dead_sessions_are_cleaned_on_push() throws Exception {
        TicketId tid = new TicketId("t3");
        WebSocketSession dead = closedSession("dead");
        WebSocketSession alive = openSession("alive");

        registry.register(tid, dead);
        registry.register(tid, alive);
        assertEquals(1, registry.activeTicketCount());

        registry.pushToTicket(tid, "{\"type\":\"ping\"}");

        // Only the alive session should receive the message
        verify(alive).sendMessage(any(TextMessage.class));
        verify(dead, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void push_removes_session_on_ioexception() throws Exception {
        TicketId tid = new TicketId("t4");
        WebSocketSession session = openSession("s4");
        doThrow(new IOException("simulated error"))
                .when(session).sendMessage(any(TextMessage.class));

        registry.register(tid, session);
        registry.pushToTicket(tid, "{}");

        // After IOException, session removed — second push hits empty set
        verify(session, times(1)).sendMessage(any(TextMessage.class));
        assertEquals(0, registry.sessionsForTicket(tid).size());
    }

    @Test
    void pushStatus_sends_status_frame() throws Exception {
        TicketId tid = new TicketId("t5");
        List<String> captured = new ArrayList<>();
        WebSocketSession session = openSession("s5");
        doAnswer(inv -> {
            TextMessage msg = inv.getArgument(0);
            captured.add(msg.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));

        registry.register(tid, session);
        registry.pushStatus(tid, "SOLVED");

        assertEquals(1, captured.size());
        assertTrue(captured.get(0).contains("\"status\""));
        assertTrue(captured.get(0).contains("SOLVED"));
    }

    @Test
    void multiple_sessions_per_ticket() throws Exception {
        TicketId tid = new TicketId("t6");
        WebSocketSession s1 = openSession("s6a");
        WebSocketSession s2 = openSession("s6b");

        registry.register(tid, s1);
        registry.register(tid, s2);

        registry.pushToTicket(tid, "{}");
        verify(s1).sendMessage(any(TextMessage.class));
        verify(s2).sendMessage(any(TextMessage.class));
    }
}
