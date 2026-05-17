package dev.autotix.interfaces.desk;

import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP-level tests for GET /api/desk/reports/summary.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportsControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private String getAdminToken() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse resp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(resp, "Login response must not be null");
        return resp.accessToken;
    }

    @Test
    void summary_withValidToken_returns200() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ReportsSummaryDTO> resp = rest.exchange(
                base() + "/api/desk/reports/summary",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ReportsSummaryDTO.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    void summary_withoutToken_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/desk/reports/summary", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void summary_hasAllTopLevelKeys() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/desk/reports/summary",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map body = resp.getBody();
        assertNotNull(body);

        assertTrue(body.containsKey("openTickets"), "Must have openTickets");
        assertTrue(body.containsKey("solvedToday"), "Must have solvedToday");
        assertTrue(body.containsKey("slaBreachRatePct"), "Must have slaBreachRatePct");
        assertTrue(body.containsKey("createdSeries"), "Must have createdSeries");
        assertTrue(body.containsKey("solvedSeries"), "Must have solvedSeries");
        assertTrue(body.containsKey("byChannel"), "Must have byChannel");
        assertTrue(body.containsKey("byAgent"), "Must have byAgent");
    }

    @Test
    void summary_createdSeriesIs14Days() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/desk/reports/summary",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map body = resp.getBody();
        assertNotNull(body);

        Object createdSeries = body.get("createdSeries");
        assertNotNull(createdSeries);
        assertTrue(createdSeries instanceof List, "createdSeries must be an array");
        assertEquals(14, ((List<?>) createdSeries).size(), "createdSeries must have 14 entries");
    }
}
