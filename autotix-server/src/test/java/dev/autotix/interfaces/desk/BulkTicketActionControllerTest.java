package dev.autotix.interfaces.desk;

import dev.autotix.domain.channel.Channel;
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
import dev.autotix.interfaces.desk.dto.BulkTicketActionRequest;
import dev.autotix.interfaces.desk.dto.BulkTicketActionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for POST /api/desk/tickets/bulk
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BulkTicketActionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    ChannelRepository channelRepository;

    private String adminToken;
    private Channel channel;

    private String base() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse resp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(resp);
        adminToken = resp.accessToken;

        // Create a channel
        String token = "bulk-test-token-" + System.currentTimeMillis();
        Channel ch = Channel.rehydrate(
                null,
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Bulk Test Channel",
                token,
                null,
                true,
                false,
                Instant.now(),
                Instant.now());
        channelRepository.save(ch);
        channel = channelRepository.findByWebhookToken(PlatformType.ZENDESK, token)
                .orElseThrow(() -> new AssertionError("channel not found"));
    }

    private String saveTicket(String extId) {
        Message msg = new Message(MessageDirection.INBOUND, "bulk@test.com", "help me", Instant.now());
        Ticket t = Ticket.openFromInbound(channel.id(), extId, "Bulk subject", "bulk@test.com", msg);
        ticketRepository.save(t);
        return ticketRepository.findByChannelAndExternalId(channel.id(), extId)
                .orElseThrow(() -> new AssertionError("ticket not saved"))
                .id().value();
    }

    private HttpEntity<BulkTicketActionRequest> authedRequest(BulkTicketActionRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(req, headers);
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    void bulk_solve_happyPath_returns200WithSuccessCount() {
        long ts = System.currentTimeMillis();
        String t1 = saveTicket("bulk-ext-1-" + ts);
        String t2 = saveTicket("bulk-ext-2-" + ts);
        String t3 = saveTicket("bulk-ext-3-" + ts);

        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Arrays.asList(t1, t2, t3);
        req.action = "SOLVE";
        req.payload = Collections.emptyMap();

        ResponseEntity<BulkTicketActionResponse> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                authedRequest(req),
                BulkTicketActionResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        BulkTicketActionResponse body = resp.getBody();
        assertNotNull(body);
        assertEquals(3, body.successCount);
        assertTrue(body.failures.isEmpty());
    }

    @Test
    void bulk_addTag_tagsAllTickets() {
        long ts = System.currentTimeMillis();
        String t1 = saveTicket("bulk-tag-1-" + ts);
        String t2 = saveTicket("bulk-tag-2-" + ts);

        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Arrays.asList(t1, t2);
        req.action = "ADD_TAG";
        req.payload = new HashMap<>();
        req.payload.put("tag", "bulk-test");

        ResponseEntity<BulkTicketActionResponse> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                authedRequest(req),
                BulkTicketActionResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(2, resp.getBody().successCount);
    }

    @Test
    void bulk_withOneBogusId_partialSuccess() {
        long ts = System.currentTimeMillis();
        String t1 = saveTicket("bulk-bogus-1-" + ts);

        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Arrays.asList(t1, "does-not-exist-9999");
        req.action = "SOLVE";
        req.payload = Collections.emptyMap();

        ResponseEntity<BulkTicketActionResponse> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                authedRequest(req),
                BulkTicketActionResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        BulkTicketActionResponse body = resp.getBody();
        assertNotNull(body);
        assertEquals(1, body.successCount);
        assertEquals(1, body.failures.size());
        assertEquals("does-not-exist-9999", body.failures.get(0).ticketId);
    }

    // ── Auth guard ───────────────────────────────────────────────────────────

    @Test
    void bulk_noAuth_returns401() {
        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Collections.singletonList("t-1");
        req.action = "SOLVE";
        req.payload = Collections.emptyMap();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void bulk_emptyTicketIds_returns400() {
        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Collections.emptyList();
        req.action = "SOLVE";
        req.payload = Collections.emptyMap();

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                authedRequest(req),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    void bulk_missingAction_returns400() {
        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.ticketIds = Collections.singletonList("t-1");
        req.action = null;
        req.payload = Collections.emptyMap();

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/bulk",
                HttpMethod.POST,
                authedRequest(req),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }
}
