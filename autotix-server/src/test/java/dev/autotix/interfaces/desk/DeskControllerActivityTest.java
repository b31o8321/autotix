package dev.autotix.interfaces.desk;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.desk.dto.TicketActivityDTO;
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
 * Integration test for DeskController activity endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DeskControllerActivityTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    TicketRepository ticketRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    TicketActivityRepository activityRepository;

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

    private Ticket saveTicketWithActivity() {
        String channelToken = "activity-test-token-" + System.currentTimeMillis();
        Channel channel = Channel.rehydrate(
                null,
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Activity Test Channel",
                channelToken,
                null,
                true,
                false,
                Instant.now(),
                Instant.now());
        channelRepository.save(channel);

        Channel savedChannel = channelRepository.findByWebhookToken(PlatformType.ZENDESK, channelToken)
                .orElseThrow(() -> new AssertionError("Channel not found"));

        Message msg = new Message(MessageDirection.INBOUND, "user@test.com", "Need help", Instant.now());
        Ticket ticket = Ticket.openFromInbound(
                savedChannel.id(),
                "ext-activity-" + System.currentTimeMillis(),
                "Activity test ticket",
                "user@test.com",
                msg);
        ticketRepository.save(ticket);

        activityRepository.save(new TicketActivity(
                ticket.id(), "customer", TicketActivityAction.CREATED, Instant.now()));

        return ticket;
    }

    @Test
    void activity_withNoAuth_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/desk/tickets/1/activity", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void activity_withAuth_returns200AndEntries() {
        Ticket ticket = saveTicketWithActivity();
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<TicketActivityDTO[]> resp = rest.exchange(
                base() + "/api/desk/tickets/" + ticket.id().value() + "/activity",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TicketActivityDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().length >= 1);
        assertEquals("CREATED", resp.getBody()[0].action);
    }
}
