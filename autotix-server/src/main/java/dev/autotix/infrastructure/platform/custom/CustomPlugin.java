package dev.autotix.infrastructure.platform.custom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic test/custom plugin for end-to-end verification without an external platform.
 *
 * <p>Webhook payload (POST /v2/webhook/CUSTOM/&lt;token&gt;):
 * <pre>
 * {
 *   "externalTicketId": "t1",
 *   "customerIdentifier": "user@example.com",
 *   "customerName": "John",        (optional)
 *   "subject": "Help with order",  (optional)
 *   "message": "I need help",
 *   "eventType": "NEW_TICKET"       (optional: NEW_TICKET / NEW_MESSAGE / IGNORED, default NEW_MESSAGE)
 * }
 * </pre>
 *
 * <p>sendReply: logs to console (no external HTTP call).
 * <p>close: no-op.
 * <p>healthCheck: always true.
 * <p>No signature verification (accepts any payload from anyone holding the webhookToken).
 */
@Component
public class CustomPlugin implements TicketPlatformPlugin {

    private static final Logger log = LoggerFactory.getLogger(CustomPlugin.class);

    @Override
    public PlatformType platform() {
        return PlatformType.CUSTOM;
    }

    @Override
    public ChannelType defaultChannelType() {
        return ChannelType.CHAT;
    }

    @Override
    public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            throw new AutotixException.ValidationException("CUSTOM webhook payload must be non-empty JSON");
        }
        JSONObject json;
        try {
            json = JSON.parseObject(rawBody);
        } catch (Exception e) {
            throw new AutotixException.ValidationException("CUSTOM webhook payload is not valid JSON: " + e.getMessage());
        }

        String externalTicketId = json.getString("externalTicketId");
        if (externalTicketId == null || externalTicketId.trim().isEmpty()) {
            throw new AutotixException.ValidationException("externalTicketId is required");
        }

        EventType eventType;
        String evtRaw = json.getString("eventType");
        if (evtRaw == null) {
            eventType = EventType.NEW_MESSAGE;
        } else {
            try {
                eventType = EventType.valueOf(evtRaw.toUpperCase());
            } catch (IllegalArgumentException e) {
                eventType = EventType.IGNORED;
            }
        }

        Map<String, Object> raw = new HashMap<>(json);
        return new TicketEvent(
                channel.id(),
                eventType,
                externalTicketId,
                json.getString("customerIdentifier"),
                json.getString("customerName"),
                json.getString("subject"),
                json.getString("message"),
                Instant.now(),
                raw);
    }

    @Override
    public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
        // No external system — just log so the operator can see the reply was generated.
        log.info("[CUSTOM] reply for ticket externalId={} (channel={}): {}",
                ticket.externalNativeId(), channel.displayName(), formattedReply);
    }

    @Override
    public void close(Channel channel, Ticket ticket) {
        log.info("[CUSTOM] close ticket externalId={} (channel={})",
                ticket.externalNativeId(), channel.displayName());
    }

    @Override
    public boolean healthCheck(ChannelCredential credential) {
        // No external API → always healthy. Useful for connecting via the Add Channel UI.
        return true;
    }
}
