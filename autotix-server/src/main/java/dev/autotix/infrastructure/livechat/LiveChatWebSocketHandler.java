package dev.autotix.infrastructure.livechat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.application.ticket.ProcessWebhookUseCase;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the native WebSocket transport for the LiveChat widget.
 *
 * URL pattern: /ws/livechat/{channelToken}/{sessionId}
 *
 * Session attributes stored:
 *   "channelId"           — resolved on connect
 *   "channel"             — Channel aggregate
 *   "sessionId"           — visitor-generated UUID (from URL)
 *   "customerIdentifier"  — from hello frame (or sessionId as fallback)
 *   "customerName"        — from hello frame (nullable)
 *   "ticketId"            — set after first real message creates/finds ticket
 */
@Component
public class LiveChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveChatWebSocketHandler.class);

    private final ChannelRepository channelRepository;
    private final TicketRepository ticketRepository;
    private final ProcessWebhookUseCase processWebhookUseCase;
    private final LiveChatSessionRegistry registry;

    public LiveChatWebSocketHandler(ChannelRepository channelRepository,
                                    TicketRepository ticketRepository,
                                    ProcessWebhookUseCase processWebhookUseCase,
                                    LiveChatSessionRegistry registry) {
        this.channelRepository = channelRepository;
        this.ticketRepository = ticketRepository;
        this.processWebhookUseCase = processWebhookUseCase;
        this.registry = registry;
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // URL: /ws/livechat/{token}/{sessionId}
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        // parts: ["", "ws", "livechat", "{token}", "{sessionId}"]
        if (parts.length < 5) {
            log.warn("[LiveChat] bad URL path: {}", path);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        String token = parts[3];
        String sessionId = parts[4];

        Optional<Channel> channelOpt = channelRepository.findByWebhookToken(PlatformType.LIVECHAT, token);
        if (!channelOpt.isPresent()) {
            log.warn("[LiveChat] no LIVECHAT channel found for token={}", token);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        Channel channel = channelOpt.get();
        Map<String, Object> attrs = session.getAttributes();
        attrs.put("channel", channel);
        attrs.put("channelId", channel.id().value());
        attrs.put("sessionId", sessionId);
        // Default customerIdentifier to sessionId; can be overridden by hello frame
        attrs.put("customerIdentifier", sessionId);

        log.info("[LiveChat] connected session={} channel={} sessionId={}", session.getId(), channel.id().value(), sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        JSONObject json;
        try {
            json = JSON.parseObject(payload);
        } catch (Exception e) {
            sendError(session, "invalid JSON: " + e.getMessage());
            return;
        }

        String type = json.getString("type");
        if (type == null) {
            sendError(session, "missing 'type' field");
            return;
        }

        switch (type) {
            case "hello":
                handleHello(session, json);
                break;
            case "message":
                handleMessage(session, json);
                break;
            case "typing":
                // No-op for v1; frame received and ignored
                break;
            default:
                log.debug("[LiveChat] unknown frame type={} session={}", type, session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        registry.unregister(session);
        log.info("[LiveChat] disconnected session={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("[LiveChat] transport error session={}: {}", session.getId(), exception.getMessage());
        registry.unregister(session);
    }

    // -----------------------------------------------------------------------
    // Frame handlers
    // -----------------------------------------------------------------------

    private void handleHello(WebSocketSession session, JSONObject json) {
        Map<String, Object> attrs = session.getAttributes();
        String identifier = json.getString("customerIdentifier");
        String name = json.getString("customerName");
        if (identifier != null && !identifier.trim().isEmpty()) {
            attrs.put("customerIdentifier", identifier.trim());
        }
        if (name != null && !name.trim().isEmpty()) {
            attrs.put("customerName", name.trim());
        }
        log.debug("[LiveChat] hello session={} identifier={}", session.getId(), attrs.get("customerIdentifier"));
    }

    private void handleMessage(WebSocketSession session, JSONObject json) throws Exception {
        Map<String, Object> attrs = session.getAttributes();
        Channel channel = (Channel) attrs.get("channel");
        if (channel == null) {
            sendError(session, "channel not resolved; reconnect");
            return;
        }

        String content = json.getString("content");
        if (content == null || content.trim().isEmpty()) {
            sendError(session, "message content must not be empty");
            return;
        }

        String sessionId = (String) attrs.get("sessionId");
        String customerIdentifier = (String) attrs.get("customerIdentifier");
        String customerName = (String) attrs.get("customerName");

        // Build TicketEvent — externalTicketId = sessionId (stable visitor UUID)
        TicketEvent event = new TicketEvent(
                channel.id(),
                EventType.NEW_MESSAGE,
                sessionId,
                customerIdentifier,
                customerName,
                null,   // subject
                content.trim(),
                Instant.now(),
                Collections.emptyMap(),
                Collections.emptyList()
        );

        processWebhookUseCase.handle(channel, event);

        // After processing, resolve the ticket and register session
        Optional<Ticket> ticketOpt = ticketRepository.findByChannelAndExternalId(channel.id(), sessionId);
        if (ticketOpt.isPresent()) {
            Ticket ticket = ticketOpt.get();
            TicketId ticketId = ticket.id();
            attrs.put("ticketId", ticketId.value());
            // Register session so replies can be pushed back
            registry.register(ticketId, session);

            // Send ready frame with ticketId
            Map<String, Object> ready = new LinkedHashMap<>();
            ready.put("type", "ready");
            ready.put("ticketId", ticketId.value());
            session.sendMessage(new TextMessage(JSON.toJSONString(ready)));
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void sendError(WebSocketSession session, String msg) {
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "error");
            frame.put("message", msg);
            session.sendMessage(new TextMessage(JSON.toJSONString(frame)));
        } catch (Exception e) {
            log.warn("[LiveChat] failed to send error frame: {}", e.getMessage());
        }
    }
}
