package dev.autotix.infrastructure.platform.custom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    private final StorageProvider storageProvider;

    public CustomPlugin(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

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

        // Slice 11: process inbound attachments (base64-encoded)
        List<TicketEvent.InboundAttachment> attachments = new ArrayList<>();
        JSONArray attsArr = json.getJSONArray("attachments");
        if (attsArr != null) {
            for (int i = 0; i < attsArr.size(); i++) {
                JSONObject att = attsArr.getJSONObject(i);
                String attFileName = att.getString("fileName");
                String attContentType = att.getString("contentType");
                long attSizeBytes = att.getLongValue("sizeBytes");
                String base64 = att.getString("contentBase64");
                if (base64 != null && !base64.isEmpty()) {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(base64);
                        String safeFileName = (attFileName != null ? attFileName : "file")
                                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
                        Date now = new Date();
                        String year = String.format("%tY", now);
                        String month = String.format("%tm", now);
                        String key = "attachments/" + year + "/" + month + "/inbound/"
                                + UUID.randomUUID().toString() + "-" + safeFileName;
                        storageProvider.upload(key,
                                new ByteArrayInputStream(bytes),
                                bytes.length,
                                attContentType != null ? attContentType : "application/octet-stream");
                        attachments.add(new TicketEvent.InboundAttachment(
                                attFileName, attContentType, attSizeBytes, key, "customer"));
                        log.debug("[CUSTOM] stored inbound attachment {} -> {}", attFileName, key);
                    } catch (Exception e) {
                        log.warn("[CUSTOM] failed to store inbound attachment {}: {}", attFileName, e.getMessage());
                    }
                }
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
                raw,
                attachments);
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
