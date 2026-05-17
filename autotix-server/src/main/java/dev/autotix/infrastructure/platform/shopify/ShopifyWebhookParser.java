package dev.autotix.infrastructure.platform.shopify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses and verifies Shopify webhook payloads.
 *
 * <h3>Supported topics (via {@code X-Shopify-Topic} header)</h3>
 * <ul>
 *   <li>{@code orders/create}     → NEW_TICKET, subject "New order #{order_number}"</li>
 *   <li>{@code orders/cancelled}  → NEW_TICKET (priority HIGH), subject "Order #{order_number} cancelled"</li>
 *   <li>{@code customers/create}  → NEW_TICKET, subject "New customer signup: {first_name} {last_name}"</li>
 *   <li>Any other topic          → IGNORED</li>
 * </ul>
 *
 * <h3>Signature verification</h3>
 * HMAC-SHA256 of the raw request body signed with the {@code webhook_shared_secret},
 * base64-encoded, compared with the {@code X-Shopify-Hmac-Sha256} header.
 * If no secret is configured, the webhook is allowed through with a warning log.
 */
@Component
public class ShopifyWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(ShopifyWebhookParser.class);

    private static final String TOPIC_HEADER     = "x-shopify-topic";
    private static final String HMAC_HEADER      = "x-shopify-hmac-sha256";
    private static final String SHOP_HEADER      = "x-shopify-shop-domain";
    private static final String HMAC_ALGORITHM   = "HmacSHA256";

    // -----------------------------------------------------------------------
    // Signature verification
    // -----------------------------------------------------------------------

    /**
     * Verify the HMAC-SHA256 signature of the raw webhook body.
     *
     * <p>If {@code secret} is null or blank returns {@code true} (permissive / dev mode).
     *
     * @return {@code true} if the signature is valid or no secret configured; {@code false} on mismatch
     */
    public boolean verifySignature(Map<String, String> headers, String rawBody, String secret) {
        if (secret == null || secret.isEmpty()) {
            return true;
        }

        String providedSig = findHeader(headers, HMAC_HEADER);
        if (providedSig == null || providedSig.isEmpty()) {
            return false;
        }

        try {
            byte[] providedBytes = Base64.getDecoder().decode(providedSig);

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] computedBytes = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(computedBytes, providedBytes);
        } catch (Exception e) {
            log.warn("[SHOPIFY] Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /**
     * Parse the raw Shopify webhook payload into a {@link TicketEvent}.
     *
     * <p>Priority metadata is stored in the raw map as {@code "priority"} so that
     * {@link ShopifyPlugin} can apply it after the event is created.
     */
    public TicketEvent parse(Channel channel, Map<String, String> headers, String rawBody) {
        String topic    = nullToEmpty(findHeader(headers, TOPIC_HEADER));
        String shopDomain = nullToEmpty(findHeader(headers, SHOP_HEADER));

        JSONObject root = JSON.parseObject(rawBody);
        if (root == null) {
            root = new JSONObject();
        }

        Instant now = Instant.now();
        String externalId;
        String subject;
        String customerIdentifier;
        String customerName;
        String messageBody;
        EventType eventType = EventType.NEW_TICKET;
        String priority = null;

        switch (topic) {
            case "orders/create": {
                externalId          = nullToEmpty(root.getString("id"));
                String orderNum     = nullToEmpty(root.getString("order_number"));
                String totalPrice   = nullToEmpty(root.getString("total_price"));
                subject             = "New order #" + orderNum;
                JSONObject customer = root.getJSONObject("customer");
                customerIdentifier  = extractEmail(customer, root);
                customerName        = extractName(customer);
                JSONArray lineItems = root.getJSONArray("line_items");
                int itemCount       = lineItems != null ? lineItems.size() : 0;
                messageBody = "Order #" + orderNum + " created."
                        + " Total: " + totalPrice
                        + ". Line items: " + itemCount
                        + ". Customer: " + customerName
                        + " <" + customerIdentifier + ">";
                break;
            }
            case "orders/cancelled": {
                externalId          = nullToEmpty(root.getString("id"));
                String orderNum     = nullToEmpty(root.getString("order_number"));
                subject             = "Order #" + orderNum + " cancelled";
                JSONObject customer = root.getJSONObject("customer");
                customerIdentifier  = extractEmail(customer, root);
                customerName        = extractName(customer);
                String reason       = nullToEmpty(root.getString("cancel_reason"));
                messageBody = "Order #" + orderNum + " was cancelled."
                        + (reason.isEmpty() ? "" : " Reason: " + reason)
                        + ". Customer: " + customerName
                        + " <" + customerIdentifier + ">";
                priority = "HIGH";
                break;
            }
            case "customers/create": {
                externalId         = nullToEmpty(root.getString("id"));
                String firstName   = nullToEmpty(root.getString("first_name"));
                String lastName    = nullToEmpty(root.getString("last_name"));
                customerName       = (firstName + " " + lastName).trim();
                customerIdentifier = nullToEmpty(root.getString("email"));
                subject            = "New customer signup: " + customerName;
                messageBody = "New Shopify customer registered."
                        + " Name: " + customerName
                        + " <" + customerIdentifier + ">"
                        + " Shop: " + shopDomain;
                break;
            }
            default: {
                log.info("[SHOPIFY] Ignoring unhandled topic: {}", topic);
                externalId         = nullToEmpty(root.getString("id"));
                subject            = "Shopify event: " + topic;
                customerIdentifier = "";
                customerName       = "";
                messageBody        = rawBody;
                eventType          = EventType.IGNORED;
                break;
            }
        }

        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("topic", topic);
        raw.put("shop", shopDomain);
        raw.put("body", rawBody);
        if (priority != null) {
            raw.put("priority", priority);
        }

        return new TicketEvent(
                channel.id(),
                eventType,
                externalId,
                customerIdentifier,
                customerName,
                subject,
                messageBody,
                now,
                raw,
                Collections.<TicketEvent.InboundAttachment>emptyList());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String extractEmail(JSONObject customer, JSONObject root) {
        if (customer != null) {
            String e = customer.getString("email");
            if (e != null && !e.isEmpty()) return e;
        }
        // Some order webhooks have email at root level
        String e = root.getString("email");
        return e != null ? e : "";
    }

    private String extractName(JSONObject customer) {
        if (customer == null) return "";
        String first = nullToEmpty(customer.getString("first_name"));
        String last  = nullToEmpty(customer.getString("last_name"));
        return (first + " " + last).trim();
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
