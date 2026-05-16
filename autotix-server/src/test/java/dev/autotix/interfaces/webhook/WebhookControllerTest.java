package dev.autotix.interfaces.webhook;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketRepository;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full SpringBootTest for WebhookController.
 * Tests the complete webhook processing pipeline including:
 * - Platform lookup
 * - Token validation
 * - Signature verification
 * - Ticket creation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebhookControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    TicketRepository ticketRepository;

    private String base() {
        return "http://localhost:" + port;
    }

    @Test
    void unknownPlatform_returns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/v2/webhook/UNKNOWN_PLATFORM_XYZ/sometoken",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void unknownToken_returns404() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/v2/webhook/ZENDESK/nonexistent-token-abc123",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void validSignedRequest_returns200_andCreatesTicket() throws Exception {
        // Create a test channel with a known webhook_secret
        String webhookSecret = "test-webhook-secret-key";
        String webhookToken = "integration-test-token-" + System.currentTimeMillis();

        Map<String, String> attrs = new HashMap<>();
        attrs.put("webhook_secret", webhookSecret);
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);

        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "Test Zendesk");
        // Force the webhook token to our test value via rehydrate (after newInstance generates its own)
        channel = Channel.rehydrate(
                null,
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Zendesk",
                webhookToken,
                credential,
                true,
                false, // autoReply disabled so no AI call
                Instant.now(),
                Instant.now());
        channelRepository.save(channel);

        // Build a valid modern Zendesk webhook payload
        String extTicketId = String.valueOf(System.currentTimeMillis());
        String timestamp = "1717228800";
        String rawBody = "{"
                + "\"type\":\"zen:event-type:ticket.created\","
                + "\"timestamp\":\"2024-06-01T12:00:00Z\","
                + "\"event\":{\"comment\":{\"id\":1,\"body\":\"Test message body\",\"public\":true,\"attachments\":[]}},"
                + "\"detail\":{"
                + "\"id\":\"" + extTicketId + "\","
                + "\"subject\":\"Test subject\","
                + "\"requester_id\":\"1\","
                + "\"requester_email\":\"test@example.com\","
                + "\"requester_name\":\"Test User\""
                + "}"
                + "}";

        // Compute HMAC-SHA256 signature over timestamp+rawBody (modern Zendesk spec)
        String signedData = timestamp + rawBody;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(signedData.getBytes(StandardCharsets.UTF_8)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Zendesk-Webhook-Signature", signature);
        headers.set("X-Zendesk-Webhook-Signature-Timestamp", timestamp);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/v2/webhook/ZENDESK/" + webhookToken,
                HttpMethod.POST,
                new HttpEntity<>(rawBody, headers),
                String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());

        // Verify a ticket was created (find by the channel that was just saved)
        // The ticket should be findable by the external id
        Channel savedChannel = channelRepository.findByWebhookToken(PlatformType.ZENDESK, webhookToken)
                .orElseThrow(() -> new AssertionError("Channel not found"));
        Optional<Ticket> ticket = ticketRepository.findByChannelAndExternalId(
                savedChannel.id(),
                extTicketId);
        assertTrue(ticket.isPresent(), "Ticket should have been created from webhook");
    }

    @Test
    void invalidSignature_returns401() throws Exception {
        String webhookToken = "sig-test-token-" + System.currentTimeMillis();
        Map<String, String> attrs = new HashMap<>();
        attrs.put("webhook_secret", "correct-secret");
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);

        Channel channel = Channel.rehydrate(
                null,
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Sig Test",
                webhookToken,
                credential,
                true,
                false,
                Instant.now(),
                Instant.now());
        channelRepository.save(channel);

        String rawBody = "{\"type\":\"ticket.created\",\"ticket\":{\"id\":999,"
                + "\"subject\":\"x\",\"requester\":{\"id\":1,\"name\":\"u\",\"email\":\"u@e.com\"},"
                + "\"latest_comment\":{\"body\":\"body\",\"created_at\":\"2024-01-01T00:00:00Z\"}}}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Zendesk-Webhook-Signature", "wrong-signature");

        ResponseEntity<String> resp = rest.exchange(
                base() + "/v2/webhook/ZENDESK/" + webhookToken,
                HttpMethod.POST,
                new HttpEntity<>(rawBody, headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }
}
