package dev.autotix.infrastructure.platform.freshdesk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Freshdesk Automation → Webhook payloads into {@link TicketEvent}s.
 *
 * <h3>Supported payload shapes</h3>
 * <ol>
 *   <li>Nested: {@code {"freshdesk_webhook": { "ticket_id": ..., ... }}}</li>
 *   <li>Flat: {@code {"ticket_id": ..., "ticket_subject": ..., ...}}</li>
 * </ol>
 *
 * <h3>Event type mapping</h3>
 * <ul>
 *   <li>{@code ticket_created} or unknown / absent → {@link EventType#NEW_TICKET}</li>
 *   <li>{@code note_added}                         → {@link EventType#NEW_MESSAGE}</li>
 *   <li>{@code status_changed} to Resolved/Closed  → {@link EventType#STATUS_CHANGE}</li>
 *   <li>{@code status_changed} to Open/Pending     → {@link EventType#IGNORED}</li>
 * </ul>
 *
 * <h3>Secret / token verification</h3>
 * Freshdesk does not ship a standard HMAC. We support an <em>optional shared-token</em>
 * approach: if {@code channel.credential().attributes().get("webhook_secret")} is set,
 * the incoming request must carry a matching {@code X-Autotix-Webhook-Token} header.
 * Comparison is constant-time. Missing or mismatching header → {@link AutotixException.AuthException}.
 * If no secret is configured → allow with a WARN log.
 */
@Component
public class FreshdeskWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(FreshdeskWebhookParser.class);

    /** Header that the operator includes when configuring the Freshdesk webhook action. */
    private static final String TOKEN_HEADER = "x-autotix-webhook-token";

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Verify the optional shared token and parse the payload.
     *
     * <p>Call this from {@link FreshdeskPlugin#parseWebhook}.
     */
    public TicketEvent parseAndVerify(Channel channel, Map<String, String> headers, String rawBody) {
        String secret = extractSecret(channel);

        if (secret != null && !secret.isEmpty()) {
            String provided = findHeader(headers, TOKEN_HEADER);
            if (provided == null || provided.isEmpty()) {
                throw new AutotixException.AuthException(
                        "Freshdesk webhook: X-Autotix-Webhook-Token header is missing " +
                        "but webhook_secret is configured for channel " + channel.id());
            }
            if (!constantTimeEquals(secret, provided)) {
                throw new AutotixException.AuthException(
                        "Freshdesk webhook: X-Autotix-Webhook-Token header mismatch " +
                        "for channel " + channel.id());
            }
        } else {
            log.warn("[FRESHDESK] No webhook_secret configured for channel {}; " +
                     "accepting webhook without token verification.", channel.id());
        }

        return parse(channel, rawBody);
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    TicketEvent parse(Channel channel, String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);
        if (root == null) root = new JSONObject();

        // Support both nested and flat shapes
        JSONObject data;
        JSONObject nested = root.getJSONObject("freshdesk_webhook");
        if (nested != null) {
            data = nested;
        } else {
            data = root;
        }

        String ticketIdRaw     = nullToEmpty(data.getString("ticket_id"));
        String subject         = nullToEmpty(data.getString("ticket_subject"));
        String description     = nullToEmpty(data.getString("ticket_description"));
        String requesterEmail  = nullToEmpty(data.getString("ticket_requester_email"));
        String requesterName   = nullToEmpty(data.getString("ticket_requester_name"));
        String triggeredEvent  = nullToEmpty(data.getString("triggered_event"));
        String ticketStatus    = nullToEmpty(data.getString("ticket_status")).toLowerCase();

        EventType eventType = mapEventType(triggeredEvent, ticketStatus);

        // Ignore churn: Open/Pending status changes are not worth an event
        if (eventType == EventType.IGNORED) {
            log.debug("[FRESHDESK] Ignoring event '{}' with status '{}' for ticket {}",
                    triggeredEvent, ticketStatus, ticketIdRaw);
        }

        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("triggered_event", triggeredEvent);
        raw.put("ticket_status", ticketStatus);
        raw.put("body", rawBody);

        return new TicketEvent(
                channel.id(),
                eventType,
                ticketIdRaw,
                requesterEmail,
                requesterName,
                subject.isEmpty() ? null : subject,
                description,
                Instant.now(),
                raw,
                Collections.<TicketEvent.InboundAttachment>emptyList());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private EventType mapEventType(String triggeredEvent, String ticketStatus) {
        if (triggeredEvent == null || triggeredEvent.isEmpty()) {
            return EventType.NEW_TICKET;
        }
        switch (triggeredEvent) {
            case "ticket_created":
                return EventType.NEW_TICKET;
            case "note_added":
                return EventType.NEW_MESSAGE;
            case "status_changed":
                // Mirror Resolved / Closed → STATUS_CHANGE; ignore Open / Pending churn
                if (ticketStatus.contains("resolved") || ticketStatus.contains("closed")
                        || ticketStatus.equals("4") || ticketStatus.equals("5")) {
                    return EventType.STATUS_CHANGE;
                }
                return EventType.IGNORED;
            default:
                return EventType.NEW_TICKET;
        }
    }

    private String extractSecret(Channel channel) {
        if (channel.credential() == null || channel.credential().attributes() == null) {
            return null;
        }
        return channel.credential().attributes().get("webhook_secret");
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

    /** Constant-time string comparison to avoid timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
