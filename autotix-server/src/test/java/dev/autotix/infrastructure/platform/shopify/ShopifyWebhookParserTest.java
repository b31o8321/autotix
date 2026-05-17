package dev.autotix.infrastructure.platform.shopify;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShopifyWebhookParser unit tests.
 */
class ShopifyWebhookParserTest {

    private ShopifyWebhookParser parser;
    private Channel channel;

    @BeforeEach
    void setUp() {
        parser = new ShopifyWebhookParser();

        Map<String, String> attrs = new HashMap<>();
        attrs.put("shop_domain", "test-store.myshopify.com");
        attrs.put("admin_api_token", "shpat_test123");
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);

        channel = Channel.rehydrate(
                new ChannelId("ch-shopify-1"),
                PlatformType.SHOPIFY,
                ChannelType.EMAIL,
                "Test Shopify",
                "webhooktoken456",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // orders/create → NEW_TICKET with correct subject and customer fields
    // -----------------------------------------------------------------------

    @Test
    void parse_ordersCreate_producesNewTicketWithOrderSubject() {
        String body = ordersCreatePayload("1001", "cust@example.com", "Alice", "Smith", "49.99", 2);
        Map<String, String> headers = topicHeaders("orders/create");

        TicketEvent event = parser.parse(channel, headers, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertTrue(event.subject().contains("1001"), "Subject should contain order number");
        assertTrue(event.subject().startsWith("New order #"), "Subject should start with 'New order #'");
        assertEquals("cust@example.com", event.customerIdentifier());
        assertEquals("Alice Smith", event.customerName());
        assertFalse(event.messageBody().isEmpty());
        assertTrue(event.messageBody().contains("49.99"));
    }

    // -----------------------------------------------------------------------
    // orders/cancelled → NEW_TICKET with HIGH priority in raw map
    // -----------------------------------------------------------------------

    @Test
    void parse_ordersCancelled_setsHighPriorityInRaw() {
        String body = ordersCancelledPayload("2002", "bob@example.com", "Bob", "Marley", "customer");
        Map<String, String> headers = topicHeaders("orders/cancelled");

        TicketEvent event = parser.parse(channel, headers, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertTrue(event.subject().contains("2002"));
        assertTrue(event.subject().contains("cancelled"));
        assertEquals("HIGH", event.raw().get("priority"),
                "Cancelled orders should have HIGH priority in raw map");
    }

    // -----------------------------------------------------------------------
    // customers/create → NEW_TICKET with customer name in subject
    // -----------------------------------------------------------------------

    @Test
    void parse_customersCreate_producesNewTicketWithCustomerSubject() {
        String body = customersCreatePayload("carol@example.com", "Carol", "Jones");
        Map<String, String> headers = topicHeaders("customers/create");

        TicketEvent event = parser.parse(channel, headers, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertTrue(event.subject().startsWith("New customer signup:"),
                "Subject should start with 'New customer signup:'");
        assertTrue(event.subject().contains("Carol"), "Subject should contain first name");
        assertEquals("carol@example.com", event.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Unknown topic → IGNORED
    // -----------------------------------------------------------------------

    @Test
    void parse_unknownTopic_producesIgnoredEvent() {
        Map<String, String> headers = topicHeaders("products/create");
        TicketEvent event = parser.parse(channel, headers, "{\"id\":99}");

        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // Signature: valid HMAC → verifySignature returns true
    // -----------------------------------------------------------------------

    @Test
    void verifySignature_validHmac_returnsTrue() throws Exception {
        String secret = "my-webhook-secret";
        String body = "{\"id\":123}";
        String computedSig = computeHmac(secret, body);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-shopify-hmac-sha256", computedSig);

        assertTrue(parser.verifySignature(headers, body, secret));
    }

    // -----------------------------------------------------------------------
    // Signature: invalid HMAC → verifySignature returns false
    // -----------------------------------------------------------------------

    @Test
    void verifySignature_invalidHmac_returnsFalse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-shopify-hmac-sha256", Base64.getEncoder().encodeToString("bad".getBytes()));

        assertFalse(parser.verifySignature(headers, "{\"id\":1}", "real-secret"));
    }

    // -----------------------------------------------------------------------
    // Signature: no secret configured → returns true (permissive)
    // -----------------------------------------------------------------------

    @Test
    void verifySignature_noSecret_returnsTrue() {
        Map<String, String> headers = Collections.emptyMap();
        assertTrue(parser.verifySignature(headers, "{\"id\":1}", null));
        assertTrue(parser.verifySignature(headers, "{\"id\":1}", ""));
    }

    // -----------------------------------------------------------------------
    // Signature: header missing → returns false when secret configured
    // -----------------------------------------------------------------------

    @Test
    void verifySignature_missingHeader_returnsFalseWhenSecretConfigured() {
        Map<String, String> headers = Collections.emptyMap();
        assertFalse(parser.verifySignature(headers, "{\"id\":1}", "configured-secret"));
    }

    // -----------------------------------------------------------------------
    // Helper builders
    // -----------------------------------------------------------------------

    private Map<String, String> topicHeaders(String topic) {
        Map<String, String> h = new HashMap<>();
        h.put("x-shopify-topic", topic);
        h.put("x-shopify-shop-domain", "test-store.myshopify.com");
        return h;
    }

    private String ordersCreatePayload(String orderNum, String email, String firstName,
                                        String lastName, String total, int lineItemCount) {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < lineItemCount; i++) {
            if (i > 0) items.append(",");
            items.append("{\"id\":").append(i + 1).append(",\"title\":\"Item\"}");
        }
        items.append("]");
        return "{\"id\":10000" + orderNum + "," +
               "\"order_number\":\"" + orderNum + "\"," +
               "\"total_price\":\"" + total + "\"," +
               "\"email\":\"" + email + "\"," +
               "\"customer\":{\"first_name\":\"" + firstName + "\",\"last_name\":\"" + lastName + "\",\"email\":\"" + email + "\"}," +
               "\"line_items\":" + items + "}";
    }

    private String ordersCancelledPayload(String orderNum, String email, String firstName,
                                           String lastName, String reason) {
        return "{\"id\":20000" + orderNum + "," +
               "\"order_number\":\"" + orderNum + "\"," +
               "\"cancel_reason\":\"" + reason + "\"," +
               "\"customer\":{\"first_name\":\"" + firstName + "\",\"last_name\":\"" + lastName + "\",\"email\":\"" + email + "\"}}";
    }

    private String customersCreatePayload(String email, String firstName, String lastName) {
        return "{\"id\":30001," +
               "\"email\":\"" + email + "\"," +
               "\"first_name\":\"" + firstName + "\"," +
               "\"last_name\":\"" + lastName + "\"}";
    }

    private String computeHmac(String secret, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] computed = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(computed);
    }
}
