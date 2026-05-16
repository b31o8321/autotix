package dev.autotix.infrastructure.platform.zendesk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Parses and verifies Zendesk modern webhook payloads.
 *
 * <p>Expected JSON shape (Zendesk modern webhooks):
 * <pre>
 * {
 *   "type":      "zen:event-type:comment.created",
 *   "timestamp": "2024-06-01T10:00:00Z",
 *   "event": {
 *     "comment": {
 *       "id": 123,
 *       "body": "text",
 *       "public": true,
 *       "attachments": [
 *         { "content_url": "...", "file_name": "a.png", "content_type": "image/png", "size": 1234 }
 *       ]
 *     }
 *   },
 *   "detail": {
 *     "id":              "987",
 *     "subject":         "Help",
 *     "requester_id":    "456",
 *     "requester_email": "alice@example.com",
 *     "requester_name":  "Alice"
 *   }
 * }
 * </pre>
 *
 * <p>Event type mapping (substring match on {@code type}):
 * <ul>
 *   <li>contains {@code comment.created}       → {@link EventType#NEW_MESSAGE}</li>
 *   <li>contains {@code ticket.created}        → {@link EventType#NEW_TICKET}</li>
 *   <li>contains {@code ticket.status_changed} or {@code ticket.solved} → {@link EventType#STATUS_CHANGE}</li>
 *   <li>else                                   → {@link EventType#IGNORED}</li>
 * </ul>
 *
 * <p>Signature verification:
 * HMAC-SHA256 of {@code (timestamp + rawBody)} (concatenated string) with the webhook secret,
 * base64-encoded, compared to the {@code X-Zendesk-Webhook-Signature} header using constant-time
 * comparison. The timestamp is read from {@code X-Zendesk-Webhook-Signature-Timestamp}.
 */
@Component
public class ZendeskWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(ZendeskWebhookParser.class);

    private static final String SIGNATURE_HEADER           = "x-zendesk-webhook-signature";
    private static final String SIGNATURE_TIMESTAMP_HEADER = "x-zendesk-webhook-signature-timestamp";
    private static final String HMAC_ALGORITHM             = "HmacSHA256";

    private final StorageProvider storageProvider;
    private final ZendeskClient   zendeskClient;

    /** Production constructor — injected by Spring. */
    public ZendeskWebhookParser(StorageProvider storageProvider, ZendeskClient zendeskClient) {
        this.storageProvider = storageProvider;
        this.zendeskClient   = zendeskClient;
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /**
     * Parse the raw Zendesk modern-webhook payload into a {@link TicketEvent}.
     *
     * <p>Inbound attachments (if any) are downloaded via
     * {@link ZendeskClient#fetchAttachment} and uploaded to {@link StorageProvider}
     * before the event is emitted.
     */
    public TicketEvent parse(Channel channel, Map<String, String> headers, String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);

        String type      = root.getString("type");
        EventType eventType = mapEventType(type);

        // --- timestamp ---
        Instant occurredAt = Instant.now();
        String tsStr = root.getString("timestamp");
        if (tsStr != null && !tsStr.isEmpty()) {
            try { occurredAt = Instant.parse(tsStr); } catch (Exception ignored) {}
        }

        // --- detail block ---
        JSONObject detail = root.getJSONObject("detail");
        String externalTicketId   = "";
        String customerIdentifier = "";
        String customerName       = "";
        String subject            = null;

        if (detail != null) {
            externalTicketId = nullToEmpty(detail.getString("id"));
            customerIdentifier = detail.getString("requester_email");
            if (customerIdentifier == null || customerIdentifier.isEmpty()) {
                customerIdentifier = nullToEmpty(detail.getString("requester_id"));
            }
            customerName = nullToEmpty(detail.getString("requester_name"));
            if (eventType == EventType.NEW_TICKET) {
                subject = detail.getString("subject");
            }
        }

        // --- event.comment block ---
        String messageBody = "";
        List<TicketEvent.InboundAttachment> attachments = new ArrayList<TicketEvent.InboundAttachment>();

        JSONObject eventBlock = root.getJSONObject("event");
        if (eventBlock != null) {
            JSONObject comment = eventBlock.getJSONObject("comment");
            if (comment != null) {
                messageBody = nullToEmpty(comment.getString("body"));

                // Process attachments
                JSONArray attsArr = comment.getJSONArray("attachments");
                if (attsArr != null) {
                    for (int i = 0; i < attsArr.size(); i++) {
                        JSONObject att = attsArr.getJSONObject(i);
                        String contentUrl  = att.getString("content_url");
                        String fileName    = att.getString("file_name");
                        String contentType = att.getString("content_type");
                        long   sizeBytes   = att.getLongValue("size");

                        if (contentUrl == null || contentUrl.isEmpty()) {
                            continue;
                        }

                        ZendeskClient.AttachmentDownload download =
                                zendeskClient.fetchAttachment(channel.credential(), contentUrl);
                        if (download == null) {
                            log.warn("[ZENDESK] Failed to download attachment {} from {}", fileName, contentUrl);
                            continue;
                        }

                        String safeFileName = (fileName != null ? fileName : "file")
                                .replaceAll("[^a-zA-Z0-9._\\-]", "_");
                        Date now    = new Date();
                        String year = String.format("%tY", now);
                        String month = String.format("%tm", now);
                        String key  = "attachments/" + year + "/" + month
                                + "/inbound-zendesk/" + UUID.randomUUID().toString() + "-" + safeFileName;

                        try {
                            storageProvider.upload(key,
                                    new ByteArrayInputStream(download.bytes),
                                    download.bytes.length,
                                    download.contentType != null ? download.contentType
                                            : (contentType != null ? contentType : "application/octet-stream"));
                            long effectiveSize = sizeBytes > 0 ? sizeBytes : download.bytes.length;
                            attachments.add(new TicketEvent.InboundAttachment(
                                    fileName, contentType, effectiveSize, key, "customer"));
                            log.debug("[ZENDESK] stored inbound attachment {} -> {}", fileName, key);
                        } catch (Exception e) {
                            log.warn("[ZENDESK] Failed to upload attachment {} to storage: {}", fileName, e.getMessage());
                        }
                    }
                }
            }
        }

        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("body", rawBody);
        raw.put("type", type);

        return new TicketEvent(
                channel.id(),
                eventType,
                externalTicketId,
                customerIdentifier,
                customerName,
                subject,
                messageBody,
                occurredAt,
                raw,
                attachments);
    }

    // -----------------------------------------------------------------------
    // Signature verification
    // -----------------------------------------------------------------------

    /**
     * Verify the HMAC-SHA256 signature of the request.
     *
     * <p>The signed string is: {@code signatureTimestamp + rawBody} (no separator).
     * The timestamp is read from {@code X-Zendesk-Webhook-Signature-Timestamp}.
     * The signature is read from {@code X-Zendesk-Webhook-Signature}.
     *
     * <p>If {@code secret} is null or blank → returns {@code true} (dev/test mode).
     *
     * @return {@code true} if valid or no secret configured, {@code false} on mismatch/error
     */
    public boolean verifySignature(Map<String, String> headers, String rawBody, String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            return true;
        }

        String signatureHeader = findHeader(headers, SIGNATURE_HEADER);
        String timestampHeader = findHeader(headers, SIGNATURE_TIMESTAMP_HEADER);

        if (signatureHeader == null || signatureHeader.isEmpty()) {
            return false;
        }
        // timestamp is optional in the signed string only if not present; but Zendesk always sends it.
        // Use empty string if absent so we don't hard-fail on misconfigured test payloads.
        String ts = (timestampHeader != null) ? timestampHeader : "";

        try {
            // Decode the provided signature
            byte[] providedBytes = Base64.getDecoder().decode(signatureHeader);

            // Compute HMAC-SHA256(ts + rawBody, secret)
            String signedData = ts + rawBody;
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] computedBytes = mac.doFinal(signedData.getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(computedBytes, providedBytes);
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
        if (type.contains("comment.created")) {
            return EventType.NEW_MESSAGE;
        }
        if (type.contains("ticket.created")) {
            return EventType.NEW_TICKET;
        }
        if (type.contains("ticket.status_changed") || type.contains("ticket.solved")) {
            return EventType.STATUS_CHANGE;
        }
        return EventType.IGNORED;
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
