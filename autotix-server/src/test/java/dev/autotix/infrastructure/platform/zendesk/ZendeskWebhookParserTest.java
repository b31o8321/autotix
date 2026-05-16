package dev.autotix.infrastructure.platform.zendesk;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.infrastructure.infra.storage.StorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZendeskWebhookParser tests — modern Zendesk webhook payload shape.
 *
 * <p>Payload structure tested:
 * <pre>
 * { "type": "zen:event-type:comment.created", "timestamp": "...",
 *   "event": { "comment": { "body": "...", "attachments": [] } },
 *   "detail": { "id": "...", "subject": "...", "requester_email": "...", "requester_name": "..." } }
 * </pre>
 */
class ZendeskWebhookParserTest {

    private ZendeskWebhookParser parser;
    private Channel channel;
    private ZendeskClient mockClient;
    private StorageProvider mockStorage;

    @BeforeEach
    void setUp() {
        mockClient  = Mockito.mock(ZendeskClient.class);
        mockStorage = Mockito.mock(StorageProvider.class);
        parser      = new ZendeskWebhookParser(mockStorage, mockClient);

        ChannelCredential credential = new ChannelCredential(
                null, null, null,
                buildAttrs("testco", "agent@testco.com", "api-token-xyz"));
        channel = Channel.rehydrate(
                new ChannelId("channel-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Zendesk",
                "webhooktoken123",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // comment.created → NEW_MESSAGE
    // -----------------------------------------------------------------------

    @Test
    void commentCreated_mapsToNewMessage() {
        String body = "{"
                + "\"type\":\"zen:event-type:comment.created\","
                + "\"timestamp\":\"2024-06-02T12:00:00Z\","
                + "\"event\":{\"comment\":{\"id\":55,\"body\":\"Still waiting for reply.\",\"public\":true,\"attachments\":[]}},"
                + "\"detail\":{\"id\":\"99\",\"subject\":\"Re: Issue\","
                + "\"requester_id\":\"1\",\"requester_email\":\"bob@test.com\",\"requester_name\":\"Bob\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);

        assertEquals(EventType.NEW_MESSAGE, event.type());
        assertEquals("99", event.externalTicketId());
        assertEquals("Still waiting for reply.", event.messageBody());
        assertEquals("bob@test.com", event.customerIdentifier());
        assertEquals("Bob", event.customerName());
        assertNull(event.subject(), "Subject should be null for NEW_MESSAGE");
        // timestamp parsed correctly
        assertEquals(Instant.parse("2024-06-02T12:00:00Z"), event.occurredAt());
    }

    // -----------------------------------------------------------------------
    // ticket.created → NEW_TICKET (subject included)
    // -----------------------------------------------------------------------

    @Test
    void ticketCreated_mapsToNewTicketWithSubject() {
        String body = "{"
                + "\"type\":\"zen:event-type:ticket.created\","
                + "\"timestamp\":\"2024-06-01T10:00:00Z\","
                + "\"event\":{\"comment\":{\"id\":10,\"body\":\"Where is my package?\",\"public\":true,\"attachments\":[]}},"
                + "\"detail\":{\"id\":\"12345\",\"subject\":\"My order is missing\","
                + "\"requester_id\":\"789\",\"requester_email\":\"alice@example.com\",\"requester_name\":\"Alice Smith\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("12345", event.externalTicketId());
        assertEquals("My order is missing", event.subject());
        assertEquals("alice@example.com", event.customerIdentifier());
        assertEquals("Alice Smith", event.customerName());
        assertEquals("Where is my package?", event.messageBody());
        assertEquals(Instant.parse("2024-06-01T10:00:00Z"), event.occurredAt());
    }

    // -----------------------------------------------------------------------
    // ticket.status_changed → STATUS_CHANGE
    // -----------------------------------------------------------------------

    @Test
    void ticketStatusChanged_mapsToStatusChange() {
        String body = "{"
                + "\"type\":\"zen:event-type:ticket.status_changed\","
                + "\"event\":{},"
                + "\"detail\":{\"id\":\"77\",\"subject\":\"Closed\","
                + "\"requester_id\":\"2\",\"requester_email\":\"carol@test.com\",\"requester_name\":\"Carol\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals(EventType.STATUS_CHANGE, event.type());
        assertEquals("77", event.externalTicketId());
    }

    // -----------------------------------------------------------------------
    // ticket.solved → STATUS_CHANGE
    // -----------------------------------------------------------------------

    @Test
    void ticketSolved_mapsToStatusChange() {
        String body = "{"
                + "\"type\":\"zen:event-type:ticket.solved\","
                + "\"event\":{},"
                + "\"detail\":{\"id\":\"88\",\"requester_id\":\"3\",\"requester_email\":\"dave@test.com\",\"requester_name\":\"Dave\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals(EventType.STATUS_CHANGE, event.type());
    }

    // -----------------------------------------------------------------------
    // Unknown type → IGNORED
    // -----------------------------------------------------------------------

    @Test
    void unknownEventType_mapsToIgnored() {
        String body = "{"
                + "\"type\":\"zen:event-type:ticket.deleted\","
                + "\"event\":{},"
                + "\"detail\":{\"id\":\"555\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // Falls back to requester_id when email absent
    // -----------------------------------------------------------------------

    @Test
    void missingEmail_fallsBackToRequesterId() {
        String body = "{"
                + "\"type\":\"zen:event-type:comment.created\","
                + "\"event\":{\"comment\":{\"body\":\"hi\",\"attachments\":[]}},"
                + "\"detail\":{\"id\":\"10\",\"requester_id\":\"999\",\"requester_name\":\"Unknown\"}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals("999", event.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Signature verification — valid
    // -----------------------------------------------------------------------

    @Test
    void validSignature_accepted() throws Exception {
        String rawBody = "{\"type\":\"zen:event-type:ticket.created\"}";
        String secret    = "my-webhook-secret";
        String timestamp = "1717228800";
        String signature = computeHmac(timestamp + rawBody, secret);

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-zendesk-webhook-signature", signature);
        headers.put("x-zendesk-webhook-signature-timestamp", timestamp);

        assertTrue(parser.verifySignature(headers, rawBody, secret),
                "Valid HMAC signature should be accepted");
    }

    // -----------------------------------------------------------------------
    // Signature verification — tampered body rejected
    // -----------------------------------------------------------------------

    @Test
    void tamperedBody_rejected() throws Exception {
        String rawBody    = "{\"type\":\"zen:event-type:ticket.created\"}";
        String tampered   = "{\"type\":\"zen:event-type:ticket.created\",\"injected\":true}";
        String secret     = "my-webhook-secret";
        String timestamp  = "1717228800";
        // Signature is over original body, not tampered
        String signature  = computeHmac(timestamp + rawBody, secret);

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("x-zendesk-webhook-signature", signature);
        headers.put("x-zendesk-webhook-signature-timestamp", timestamp);

        assertFalse(parser.verifySignature(headers, tampered, secret),
                "Tampered body should be rejected");
    }

    // -----------------------------------------------------------------------
    // Signature verification — no secret → always true
    // -----------------------------------------------------------------------

    @Test
    void noSecret_alwaysTrue() {
        Map<String, String> headers = new HashMap<String, String>();
        assertTrue(parser.verifySignature(headers, "any-body", null));
        assertTrue(parser.verifySignature(headers, "any-body", ""));
    }

    // -----------------------------------------------------------------------
    // Signature verification — missing header → false
    // -----------------------------------------------------------------------

    @Test
    void missingSignatureHeader_false() {
        assertFalse(parser.verifySignature(
                Collections.<String, String>emptyMap(), "body", "secret"),
                "Missing signature header should return false");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String computeHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Map<String, String> buildAttrs(String subdomain, String email, String apiToken) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("subdomain", subdomain);
        m.put("email", email);
        m.put("api_token", apiToken);
        return m;
    }
}
