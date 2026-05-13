package dev.autotix.infrastructure.platform.zendesk;

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
 * ZendeskWebhookParser tests using inline JSON payloads (no fixture files).
 */
class ZendeskWebhookParserTest {

    private ZendeskWebhookParser parser;
    private Channel channel;

    @BeforeEach
    void setUp() {
        parser = new ZendeskWebhookParser();
        // Build a minimal channel for testing
        ChannelCredential credential = new ChannelCredential(
                "token-abc", null, null,
                Collections.singletonMap("subdomain", "testco"));
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
    // ticket.created -> NEW_TICKET
    // -----------------------------------------------------------------------

    @Test
    void ticketCreated_mapsToNewTicket() {
        String body = "{"
                + "\"type\":\"ticket.created\","
                + "\"ticket\":{"
                + "  \"id\":12345,"
                + "  \"subject\":\"My order is missing\","
                + "  \"requester\":{\"id\":789,\"name\":\"Alice Smith\",\"email\":\"alice@example.com\"},"
                + "  \"latest_comment\":{\"body\":\"Where is my package?\",\"created_at\":\"2024-06-01T10:00:00Z\"}"
                + "}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("12345", event.externalTicketId());
        assertEquals("My order is missing", event.subject());
        assertEquals("alice@example.com", event.customerIdentifier());
        assertEquals("Alice Smith", event.customerName());
        assertEquals("Where is my package?", event.messageBody());
        assertNotNull(event.occurredAt());
    }

    // -----------------------------------------------------------------------
    // comment.created -> NEW_MESSAGE
    // -----------------------------------------------------------------------

    @Test
    void commentCreated_mapsToNewMessage() {
        String body = "{"
                + "\"type\":\"comment.created\","
                + "\"ticket\":{"
                + "  \"id\":99,"
                + "  \"subject\":\"Re: Issue\","
                + "  \"requester\":{\"id\":1,\"name\":\"Bob\",\"email\":\"bob@test.com\"},"
                + "  \"latest_comment\":{\"body\":\"Still waiting for reply.\",\"created_at\":\"2024-06-02T12:00:00Z\"}"
                + "}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);

        assertEquals(EventType.NEW_MESSAGE, event.type());
        assertEquals("99", event.externalTicketId());
        assertEquals("Still waiting for reply.", event.messageBody());
        assertEquals("bob@test.com", event.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Unknown event type -> IGNORED
    // -----------------------------------------------------------------------

    @Test
    void unknownEventType_mapsToIgnored() {
        String body = "{"
                + "\"type\":\"ticket.deleted\","
                + "\"ticket\":{\"id\":555}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // Signature verification
    // -----------------------------------------------------------------------

    @Test
    void validSignature_accepted() throws Exception {
        String rawBody = "{\"type\":\"ticket.created\",\"ticket\":{\"id\":1}}";
        String secret = "my-webhook-secret";
        String signature = computeHmac(rawBody, secret);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-zendesk-webhook-signature", signature);

        assertTrue(parser.verifySignature(headers, rawBody, secret),
                "Valid signature should be accepted");
    }

    @Test
    void tamperedSignature_rejected() {
        String rawBody = "{\"type\":\"ticket.created\",\"ticket\":{\"id\":1}}";
        String secret = "my-webhook-secret";

        Map<String, String> headers = new HashMap<>();
        headers.put("x-zendesk-webhook-signature", "INVALID_SIGNATURE_VALUE");

        assertFalse(parser.verifySignature(headers, rawBody, secret),
                "Tampered signature should be rejected");
    }

    // -----------------------------------------------------------------------
    // ticket.updated -> STATUS_CHANGE
    // -----------------------------------------------------------------------

    @Test
    void ticketUpdated_mapsToStatusChange() {
        String body = "{"
                + "\"type\":\"ticket.updated\","
                + "\"ticket\":{\"id\":77,\"subject\":\"Closed\","
                + "\"requester\":{\"id\":2,\"name\":\"Carol\",\"email\":\"carol@test.com\"}}"
                + "}";

        TicketEvent event = parser.parse(channel, Collections.<String, String>emptyMap(), body);
        assertEquals(EventType.STATUS_CHANGE, event.type());
        assertEquals("77", event.externalTicketId());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String computeHmac(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(bytes);
    }
}
