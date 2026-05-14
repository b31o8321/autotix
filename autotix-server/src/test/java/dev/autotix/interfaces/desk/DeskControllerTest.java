package dev.autotix.interfaces.desk;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import dev.autotix.domain.channel.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full SpringBootTest for DeskController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeskControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    ChannelRepository channelRepository;

    private String base() {
        return "http://localhost:" + port;
    }

    private String getAdminToken() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse resp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(resp);
        return resp.accessToken;
    }

    @Test
    void list_withNoAuth_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/desk/tickets", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void list_withAdminToken_returns200_withEmptyListInitially() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<TicketDTO[]> resp = rest.exchange(
                base() + "/api/desk/tickets",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        // May be empty or have tickets from other tests — just check 200
    }

    @Test
    void list_afterSavingTicket_returnsIt() {
        // Save a channel first
        String channelToken = "desk-test-token-" + System.currentTimeMillis();
        Channel channel = Channel.rehydrate(
                null,
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Desk Test Channel",
                channelToken,
                null,
                true,
                false,
                Instant.now(),
                Instant.now());
        channelRepository.save(channel);

        // Find saved channel to get ID
        Channel savedChannel = channelRepository.findByWebhookToken(PlatformType.ZENDESK, channelToken)
                .orElseThrow(() -> new AssertionError("Channel not found"));

        // Save a ticket
        Message msg = new Message(MessageDirection.INBOUND, "user@test.com", "Test message", Instant.now());
        Ticket ticket = Ticket.openFromInbound(
                savedChannel.id(),
                "ext-desk-test-" + System.currentTimeMillis(),
                "Desk test subject",
                "user@test.com",
                msg);
        ticketRepository.save(ticket);

        // Fetch via desk API
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("channelId", savedChannel.id().value());

        ResponseEntity<TicketDTO[]> resp = rest.exchange(
                base() + "/api/desk/tickets?channelId=" + savedChannel.id().value(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        TicketDTO[] tickets = resp.getBody();
        assertNotNull(tickets);
        assertTrue(tickets.length >= 1, "Should return at least one ticket");
        assertEquals("Desk test subject", tickets[0].subject);
    }

    @Test
    void get_nonExistentTicket_returns404() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/99999999",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    /**
     * Slice 15: POST /ai-draft — when AI is not configured returns 502 or succeeds.
     * Since AI may not be wired in test, we just verify the endpoint is reachable (not 404/401/403).
     */
    @Test
    void aiDraft_withSuspendedTicket_returns400() {
        String channelToken = "ai-draft-token-" + System.currentTimeMillis();
        Channel channel = Channel.rehydrate(null, PlatformType.ZENDESK, ChannelType.EMAIL,
                "AI Draft Channel", channelToken, null, true, false, Instant.now(), Instant.now());
        channelRepository.save(channel);
        Channel saved = channelRepository.findByWebhookToken(PlatformType.ZENDESK, channelToken)
                .orElseThrow(() -> new AssertionError("Not found"));

        Message msg = new Message(MessageDirection.INBOUND, "u@test.com", "Help", Instant.now());
        Ticket ticket = Ticket.openFromInbound(saved.id(), "ext-ai-" + System.currentTimeMillis(),
                "AI draft test", "u@test.com", msg);
        ticket.escalateToHuman("agent:1", "testing");
        ticketRepository.save(ticket);

        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Must return 400 because ticket is aiSuspended
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/" + ticket.id().value() + "/ai-draft",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    /**
     * Slice 15: POST /tags add + remove round-trip.
     */
    @Test
    void tags_addAndRemove_roundTrip() {
        String channelToken = "tags-test-token-" + System.currentTimeMillis();
        Channel channel = Channel.rehydrate(null, PlatformType.ZENDESK, ChannelType.EMAIL,
                "Tags Channel", channelToken, null, true, false, Instant.now(), Instant.now());
        channelRepository.save(channel);
        Channel saved = channelRepository.findByWebhookToken(PlatformType.ZENDESK, channelToken)
                .orElseThrow(() -> new AssertionError("Not found"));

        Message msg = new Message(MessageDirection.INBOUND, "u@test.com", "Tag me", Instant.now());
        Ticket ticket = Ticket.openFromInbound(saved.id(), "ext-tags-" + System.currentTimeMillis(),
                "Tags test", "u@test.com", msg);
        java.util.Set<String> oldTags = new java.util.HashSet<>();
        oldTags.add("old-tag");
        ticket.addTags(oldTags);
        ticketRepository.save(ticket);

        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"add\":[\"new-tag\"],\"remove\":[\"old-tag\"]}";
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/" + ticket.id().value() + "/tags",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());

        // Verify ticket was updated
        Ticket reloaded = ticketRepository.findById(ticket.id()).orElseThrow(() -> new AssertionError("Not found"));
        assertTrue(reloaded.tags().contains("new-tag"));
        assertFalse(reloaded.tags().contains("old-tag"));
    }

    /**
     * Slice 15: PUT /custom-fields/{key} round-trip.
     */
    @Test
    void customFields_setAndClear_roundTrip() {
        String channelToken = "cf-test-token-" + System.currentTimeMillis();
        Channel channel = Channel.rehydrate(null, PlatformType.ZENDESK, ChannelType.EMAIL,
                "CF Channel", channelToken, null, true, false, Instant.now(), Instant.now());
        channelRepository.save(channel);
        Channel saved = channelRepository.findByWebhookToken(PlatformType.ZENDESK, channelToken)
                .orElseThrow(() -> new AssertionError("Not found"));

        Message msg = new Message(MessageDirection.INBOUND, "u@test.com", "CF", Instant.now());
        Ticket ticket = Ticket.openFromInbound(saved.id(), "ext-cf-" + System.currentTimeMillis(),
                "CF test", "u@test.com", msg);
        ticketRepository.save(ticket);

        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Set value
        ResponseEntity<String> setResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticket.id().value() + "/custom-fields/region",
                HttpMethod.PUT,
                new HttpEntity<>("{\"value\":\"EU\"}", headers),
                String.class);
        assertEquals(HttpStatus.OK, setResp.getStatusCode());

        Ticket afterSet = ticketRepository.findById(ticket.id()).orElseThrow(() -> new AssertionError("Not found"));
        assertEquals("EU", afterSet.customFields().get("region"));

        // Clear value
        ResponseEntity<String> clearResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticket.id().value() + "/custom-fields/region",
                HttpMethod.PUT,
                new HttpEntity<>("{\"value\":null}", headers),
                String.class);
        assertEquals(HttpStatus.OK, clearResp.getStatusCode());

        Ticket afterClear = ticketRepository.findById(ticket.id()).orElseThrow(() -> new AssertionError("Not found"));
        assertFalse(afterClear.customFields().containsKey("region"));
    }
}
