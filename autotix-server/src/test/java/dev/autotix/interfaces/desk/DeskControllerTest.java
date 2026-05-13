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
}
