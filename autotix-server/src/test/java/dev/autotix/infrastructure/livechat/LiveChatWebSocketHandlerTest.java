package dev.autotix.infrastructure.livechat;

import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiveChatWebSocketHandler.
 * Uses mocked dependencies — no Spring context needed.
 */
class LiveChatWebSocketHandlerTest {

    private ChannelRepository channelRepository;
    private TicketRepository ticketRepository;
    private ProcessWebhookUseCase processWebhookUseCase;
    private LiveChatSessionRegistry registry;
    private LiveChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        channelRepository = mock(ChannelRepository.class);
        ticketRepository = mock(TicketRepository.class);
        processWebhookUseCase = mock(ProcessWebhookUseCase.class);
        registry = mock(LiveChatSessionRegistry.class);
        handler = new LiveChatWebSocketHandler(
                channelRepository, ticketRepository, processWebhookUseCase, registry);
    }

    private WebSocketSession session(String path) throws Exception {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getId()).thenReturn("sess-1");
        when(s.getUri()).thenReturn(new URI("ws://localhost:8080" + path));
        Map<String, Object> attrs = new HashMap<>();
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    void unknown_channel_token_closes_session() throws Exception {
        when(channelRepository.findByWebhookToken(eq(PlatformType.LIVECHAT), any()))
                .thenReturn(Optional.empty());
        WebSocketSession s = session("/ws/livechat/bad-token/uuid-123");

        handler.afterConnectionEstablished(s);

        verify(s).close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Test
    void valid_hello_stores_customer_identifier() throws Exception {
        Channel channel = mockChannel("tok-1");
        when(channelRepository.findByWebhookToken(PlatformType.LIVECHAT, "tok-1"))
                .thenReturn(Optional.of(channel));

        WebSocketSession s = session("/ws/livechat/tok-1/uuid-xyz");
        handler.afterConnectionEstablished(s);

        handler.handleTextMessage(s, new TextMessage(
                "{\"type\":\"hello\",\"customerIdentifier\":\"alice@example.com\",\"customerName\":\"Alice\"}"));

        Map<String, Object> attrs = s.getAttributes();
        assert "alice@example.com".equals(attrs.get("customerIdentifier"));
        assert "Alice".equals(attrs.get("customerName"));
    }

    @Test
    void message_calls_processWebhookUseCase() throws Exception {
        Channel channel = mockChannel("tok-2");
        when(channelRepository.findByWebhookToken(PlatformType.LIVECHAT, "tok-2"))
                .thenReturn(Optional.of(channel));

        Ticket ticket = mock(Ticket.class);
        TicketId ticketId = new TicketId("ticket-42");
        when(ticket.id()).thenReturn(ticketId);
        when(ticketRepository.findByChannelAndExternalId(any(), any()))
                .thenReturn(Optional.of(ticket));

        WebSocketSession s = session("/ws/livechat/tok-2/uuid-abc");
        handler.afterConnectionEstablished(s);
        handler.handleTextMessage(s, new TextMessage(
                "{\"type\":\"message\",\"content\":\"Hello, my order is late\"}"));

        verify(processWebhookUseCase).handle(eq(channel), any(TicketEvent.class));
    }

    @Test
    void message_sends_ready_frame_with_ticket_id() throws Exception {
        Channel channel = mockChannel("tok-3");
        when(channelRepository.findByWebhookToken(PlatformType.LIVECHAT, "tok-3"))
                .thenReturn(Optional.of(channel));

        Ticket ticket = mock(Ticket.class);
        TicketId ticketId = new TicketId("ticket-99");
        when(ticket.id()).thenReturn(ticketId);
        when(ticketRepository.findByChannelAndExternalId(any(), any()))
                .thenReturn(Optional.of(ticket));

        WebSocketSession s = session("/ws/livechat/tok-3/uuid-def");
        handler.afterConnectionEstablished(s);
        handler.handleTextMessage(s, new TextMessage("{\"type\":\"message\",\"content\":\"hi\"}"));

        verify(s).sendMessage(argThat(msg ->
                msg instanceof TextMessage && ((TextMessage) msg).getPayload().contains("ticket-99")));
    }

    @Test
    void after_connection_closed_unregisters_session() throws Exception {
        Channel channel = mockChannel("tok-4");
        when(channelRepository.findByWebhookToken(PlatformType.LIVECHAT, "tok-4"))
                .thenReturn(Optional.of(channel));
        WebSocketSession s = session("/ws/livechat/tok-4/uuid-ghi");
        handler.afterConnectionEstablished(s);
        handler.afterConnectionClosed(s, CloseStatus.NORMAL);

        verify(registry).unregister(s);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Channel mockChannel(String token) {
        Channel c = mock(Channel.class);
        ChannelId cid = new ChannelId("ch-" + token);
        when(c.id()).thenReturn(cid);
        when(c.platform()).thenReturn(PlatformType.LIVECHAT);
        when(c.displayName()).thenReturn("Test LiveChat");
        return c;
    }
}
