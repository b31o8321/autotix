package dev.autotix.infrastructure.platform.line;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses and verifies LINE Messaging API webhook payloads.
 *
 * <h3>Signature verification</h3>
 * The {@code X-Line-Signature} header contains Base64(HMAC-SHA256(channel_secret, raw_body)).
 * Mismatch throws {@link AutotixException.AuthException}.
 *
 * <h3>Supported events (v1)</h3>
 * Only {@code type="message"} + {@code message.type="text"} events are converted to
 * {@link TicketEvent}s. All other event types are silently skipped.
 *
 * <h3>Multiple events</h3>
 * LINE may batch multiple events in a single POST. Each text message event yields one
 * {@link TicketEvent}. The caller handles the list.
 */
@Component
public class LineWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(LineWebhookParser.class);

    private static final String SIG_HEADER = "x-line-signature";
    private static final String HMAC_ALGO  = "HmacSHA256";

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Verify signature and parse all text message events from the payload.
     *
     * @return list of {@link TicketEvent}s (may be empty if no text events present)
     * @throws AutotixException.AuthException on invalid signature
     */
    public List<TicketEvent> parseAndVerify(Channel channel, Map<String, String> headers, String rawBody) {
        LineCredentials creds = LineCredentials.from(channel.credential());
        verifySignature(headers, rawBody, creds.channelSecret, channel.id().value());
        return parse(channel, rawBody);
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    List<TicketEvent> parse(Channel channel, String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);
        if (root == null) return Collections.emptyList();

        JSONArray events = root.getJSONArray("events");
        if (events == null || events.isEmpty()) return Collections.emptyList();

        List<TicketEvent> result = new ArrayList<TicketEvent>();

        for (int i = 0; i < events.size(); i++) {
            JSONObject event = events.getJSONObject(i);
            if (event == null) continue;

            String eventType = nullToEmpty(event.getString("type"));
            if (!"message".equals(eventType)) {
                log.debug("[LINE] Skipping event type '{}' (not 'message')", eventType);
                continue;
            }

            JSONObject message = event.getJSONObject("message");
            if (message == null) continue;

            String msgType = nullToEmpty(message.getString("type"));
            if (!"text".equals(msgType)) {
                log.debug("[LINE] Skipping message type '{}' (not 'text')", msgType);
                continue;
            }

            String text = nullToEmpty(message.getString("text"));
            JSONObject source = event.getJSONObject("source");
            String userId = source != null ? nullToEmpty(source.getString("userId")) : "";
            long timestamp = event.getLongValue("timestamp"); // ms since epoch

            String customerIdentifier = "line:" + userId;
            String subject = text.length() > 60 ? text.substring(0, 60) : text;
            if (subject.isEmpty()) subject = "LINE message";

            Instant occurredAt = timestamp > 0 ? Instant.ofEpochMilli(timestamp) : Instant.now();

            Map<String, Object> raw = new HashMap<String, Object>();
            raw.put("userId", userId);
            raw.put("eventType", eventType);
            raw.put("msgType", msgType);
            raw.put("body", rawBody);

            result.add(new TicketEvent(
                    channel.id(),
                    EventType.NEW_TICKET,
                    userId,               // externalTicketId = userId (thread key)
                    customerIdentifier,
                    "",                   // customerName — enriched separately if needed
                    subject,
                    text,
                    occurredAt,
                    raw,
                    Collections.<TicketEvent.InboundAttachment>emptyList()
            ));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    private void verifySignature(Map<String, String> headers, String rawBody, String secret, String channelId) {
        String provided = findHeader(headers, SIG_HEADER);
        if (provided == null || provided.isEmpty()) {
            throw new AutotixException.AuthException(
                    "LINE webhook: X-Line-Signature header missing for channel " + channelId);
        }

        try {
            byte[] providedBytes = Base64.getDecoder().decode(provided);

            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(keySpec);
            byte[] computedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            if (!MessageDigest.isEqual(computedBytes, providedBytes)) {
                throw new AutotixException.AuthException(
                        "LINE webhook: X-Line-Signature mismatch for channel " + channelId);
            }
        } catch (AutotixException e) {
            throw e;
        } catch (Exception e) {
            throw new AutotixException.AuthException(
                    "LINE webhook: signature verification error for channel " + channelId + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Case-insensitive header lookup. */
    private String findHeader(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String val = headers.get(name);
        if (val != null) return val;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }
}
