package dev.autotix.infrastructure.platform.zendesk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses and verifies Zendesk webhook payloads.
 *
 * <p>Supported event type mappings:
 * <ul>
 *   <li>{@code ticket.created}  -&gt; {@link EventType#NEW_TICKET}</li>
 *   <li>{@code comment.created} -&gt; {@link EventType#NEW_MESSAGE}</li>
 *   <li>{@code ticket.updated}  -&gt; {@link EventType#STATUS_CHANGE}</li>
 *   <li>Anything else           -&gt; {@link EventType#IGNORED}</li>
 * </ul>
 *
 * <p>Signature verification:
 * HMAC-SHA256 of rawBody with the webhook secret, base64-encoded, compared
 * to the {@code X-Zendesk-Webhook-Signature} header value using constant-time comparison.
 */
@Component
public class ZendeskWebhookParser {

    private static final String SIGNATURE_HEADER = "x-zendesk-webhook-signature";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Parse the raw Zendesk webhook payload into a {@link TicketEvent}.
     *
     * <p>Expected JSON shape:
     * <pre>
     * {
     *   "type": "ticket.created",
     *   "ticket": {
     *     "id": 123,
     *     "subject": "...",
     *     "requester": { "id": 456, "name": "Alice", "email": "alice@example.com" },
     *     "latest_comment": { "body": "...", "created_at": "2024-01-01T00:00:00Z" }
     *   }
     * }
     * </pre>
     */
    public TicketEvent parse(Channel channel, Map<String, String> headers, String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);

        String type = root.getString("type");
        EventType eventType = mapEventType(type);

        JSONObject ticket = root.getJSONObject("ticket");
        String ticketId = "";
        String customerIdentifier = "";
        String customerName = "";
        String subject = "";
        String messageBody = "";
        Instant occurredAt = Instant.now();

        if (ticket != null) {
            Object ticketIdObj = ticket.get("id");
            ticketId = ticketIdObj != null ? ticketIdObj.toString() : "";
            subject = nullToEmpty(ticket.getString("subject"));

            JSONObject requester = ticket.getJSONObject("requester");
            if (requester != null) {
                customerName = nullToEmpty(requester.getString("name"));
                customerIdentifier = nullToEmpty(requester.getString("email"));
                if (customerIdentifier.isEmpty()) {
                    Object reqId = requester.get("id");
                    customerIdentifier = reqId != null ? reqId.toString() : "";
                }
            }

            JSONObject latestComment = ticket.getJSONObject("latest_comment");
            if (latestComment != null) {
                messageBody = nullToEmpty(latestComment.getString("body"));
                String createdAt = latestComment.getString("created_at");
                if (createdAt != null && !createdAt.isEmpty()) {
                    try {
                        occurredAt = Instant.parse(createdAt);
                    } catch (Exception ignored) {
                        // keep Instant.now()
                    }
                }
            }
        }

        Map<String, Object> raw = new HashMap<>();
        raw.put("body", rawBody);
        raw.put("type", type);

        return new TicketEvent(
                channel.id(),
                eventType,
                ticketId,
                customerIdentifier,
                customerName,
                subject,
                messageBody,
                occurredAt,
                raw);
    }

    /**
     * Verify the HMAC-SHA256 signature of the raw body against the webhook secret.
     *
     * <p>The signature is computed as: Base64(HMAC-SHA256(rawBody, secret))
     * and compared to the {@code X-Zendesk-Webhook-Signature} header value
     * using constant-time comparison to prevent timing attacks.
     *
     * @return true if the signature matches, false otherwise
     */
    public boolean verifySignature(Map<String, String> headers, String rawBody, String webhookSecret) {
        String header = findHeader(headers, SIGNATURE_HEADER);
        if (header == null || header.isEmpty()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] computedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(computedBytes);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    header.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private EventType mapEventType(String type) {
        if (type == null) {
            return EventType.IGNORED;
        }
        switch (type) {
            case "ticket.created":  return EventType.NEW_TICKET;
            case "comment.created": return EventType.NEW_MESSAGE;
            case "ticket.updated":  return EventType.STATUS_CHANGE;
            default:                return EventType.IGNORED;
        }
    }

    /** Case-insensitive header lookup. */
    private String findHeader(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String val = headers.get(name);
        if (val != null) return val;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
