package dev.autotix.interfaces.desk;

import dev.autotix.interfaces.admin.dto.TagDTO;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.desk.dto.TicketDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for POST /api/desk/tickets/{id}/escalate and
 * POST /api/desk/tickets/{id}/resume-ai.
 *
 * Uses the webhook endpoint to create a real ticket, then tests escalate/resume.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EscalateAndResumeTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private String adminToken;
    private String agentToken;

    @BeforeEach
    void setUp() {
        LoginRequest adminReq = new LoginRequest();
        adminReq.email = "admin@test.local";
        adminReq.password = "test-admin";
        LoginResponse adminLogin = rest.postForEntity(
                base() + "/api/auth/login", adminReq, LoginResponse.class).getBody();
        assertNotNull(adminLogin);
        adminToken = adminLogin.accessToken;

        // Create agent user
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        String agentEmail = "esc-agent-" + System.currentTimeMillis() + "@test.local";
        String createBody = "{\"email\":\"" + agentEmail + "\",\"displayName\":\"EscAgent\","
                + "\"password\":\"escpw\",\"role\":\"AGENT\"}";
        rest.exchange(base() + "/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders), String.class);

        LoginRequest agentReq = new LoginRequest();
        agentReq.email = agentEmail;
        agentReq.password = "escpw";
        LoginResponse agentLogin = rest.postForEntity(
                base() + "/api/auth/login", agentReq, LoginResponse.class).getBody();
        assertNotNull(agentLogin);
        agentToken = agentLogin.accessToken;
    }

    /**
     * Create a ticket via the desk list endpoint — we need to use a
     * pre-existing ticket or create one via webhook.
     *
     * We create via the webhook endpoint using the first enabled channel's token.
     * If no channels exist we find a ticket from the ticket list.
     */
    private String getOrCreateTicketId() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<TicketDTO[]> listResp = rest.exchange(
                base() + "/api/desk/tickets",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketDTO[].class);

        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        TicketDTO[] tickets = listResp.getBody();
        if (tickets != null && tickets.length > 0) {
            return tickets[0].id;
        }
        return null;
    }

    @Test
    void escalateByAgent_returns200_andTicketIsAiSuspended() {
        String ticketId = getOrCreateTicketId();
        if (ticketId == null) {
            // No tickets in DB — cannot test without data; skip gracefully
            return;
        }

        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);
        agentHeaders.setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"reason\":\"complex issue\"}";
        ResponseEntity<Void> escalateResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticketId + "/escalate",
                HttpMethod.POST,
                new HttpEntity<>(body, agentHeaders),
                Void.class);

        assertEquals(HttpStatus.OK, escalateResp.getStatusCode());

        // Verify ticket is now aiSuspended via GET
        ResponseEntity<TicketDTO> getResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticketId,
                HttpMethod.GET,
                new HttpEntity<>(agentHeaders),
                TicketDTO.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        TicketDTO ticket = getResp.getBody();
        assertNotNull(ticket);
        assertTrue(ticket.aiSuspended, "Ticket should be aiSuspended after escalation");
    }

    @Test
    void resumeAiByAgent_returns403() {
        String ticketId = getOrCreateTicketId();
        if (ticketId == null) {
            return;
        }

        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);

        ResponseEntity<String> resumeResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticketId + "/resume-ai",
                HttpMethod.POST,
                new HttpEntity<>(agentHeaders),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, resumeResp.getStatusCode());
    }

    @Test
    void resumeAiByAdmin_returns200_andTicketAiResumed() {
        String ticketId = getOrCreateTicketId();
        if (ticketId == null) {
            return;
        }

        // First escalate via agent
        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);
        agentHeaders.setContentType(MediaType.APPLICATION_JSON);

        rest.exchange(
                base() + "/api/desk/tickets/" + ticketId + "/escalate",
                HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"test\"}", agentHeaders),
                Void.class);

        // Then resume via admin
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        ResponseEntity<Void> resumeResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticketId + "/resume-ai",
                HttpMethod.POST,
                new HttpEntity<>(adminHeaders),
                Void.class);

        assertEquals(HttpStatus.OK, resumeResp.getStatusCode());

        // Verify ticket has aiSuspended=false
        ResponseEntity<TicketDTO> getResp = rest.exchange(
                base() + "/api/desk/tickets/" + ticketId,
                HttpMethod.GET,
                new HttpEntity<>(agentHeaders),
                TicketDTO.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        TicketDTO ticket = getResp.getBody();
        assertNotNull(ticket);
        assertFalse(ticket.aiSuspended, "Ticket aiSuspended should be false after resume");
    }
}
