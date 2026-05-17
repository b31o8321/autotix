package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.NotificationRouteDTO;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NotificationAdminController (CRUD + test endpoint + 401/403 guards).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationAdminControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port + "/api/admin/notifications/routes";
    }

    private String getAdminToken() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse resp = rest.postForEntity(
                "http://localhost:" + port + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(resp);
        return resp.accessToken;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private NotificationRouteDTO emailRouteDTO() {
        NotificationRouteDTO dto = new NotificationRouteDTO();
        dto.name = "SLA Email Alert " + System.currentTimeMillis();
        dto.eventKind = "SLA_BREACHED";
        dto.channel = "EMAIL";
        dto.configJson = "{\"to\":[\"ops@example.com\"],\"subjectTemplate\":\"SLA breach on {ticketId}\"}";
        dto.enabled = true;
        return dto;
    }

    private NotificationRouteDTO slackRouteDTO() {
        NotificationRouteDTO dto = new NotificationRouteDTO();
        dto.name = "SLA Slack Alert " + System.currentTimeMillis();
        dto.eventKind = "SLA_BREACHED";
        dto.channel = "SLACK_WEBHOOK";
        dto.configJson = "{\"webhookUrl\":\"https://hooks.slack.com/test\",\"messageTemplate\":\"SLA on {ticketId}\"}";
        dto.enabled = true;
        return dto;
    }

    // -----------------------------------------------------------------------
    // Auth guards
    // -----------------------------------------------------------------------

    @Test
    void list_withoutAuth_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(base(), String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    // -----------------------------------------------------------------------
    // CRUD round-trip
    // -----------------------------------------------------------------------

    @Test
    void create_and_list_email_route() {
        String token = getAdminToken();
        HttpHeaders headers = authHeaders(token);

        NotificationRouteDTO dto = emailRouteDTO();
        ResponseEntity<NotificationRouteDTO> createResp = rest.exchange(
                base(), HttpMethod.POST, new HttpEntity<>(dto, headers), NotificationRouteDTO.class);

        assertEquals(HttpStatus.OK, createResp.getStatusCode());
        NotificationRouteDTO created = createResp.getBody();
        assertNotNull(created);
        assertNotNull(created.id);
        assertEquals("EMAIL", created.channel);
        assertEquals("SLA_BREACHED", created.eventKind);
        assertTrue(created.enabled);

        // Appears in list
        ResponseEntity<NotificationRouteDTO[]> listResp = rest.exchange(
                base(), HttpMethod.GET, new HttpEntity<>(headers), NotificationRouteDTO[].class);
        assertEquals(HttpStatus.OK, listResp.getStatusCode());
        NotificationRouteDTO[] all = listResp.getBody();
        assertNotNull(all);
        boolean found = false;
        for (NotificationRouteDTO r : all) {
            if (created.id.equals(r.id)) { found = true; break; }
        }
        assertTrue(found, "Created route should appear in list");
    }

    @Test
    void update_route() {
        String token = getAdminToken();
        HttpHeaders headers = authHeaders(token);

        NotificationRouteDTO dto = slackRouteDTO();
        NotificationRouteDTO created = rest.exchange(
                base(), HttpMethod.POST, new HttpEntity<>(dto, headers), NotificationRouteDTO.class).getBody();
        assertNotNull(created);

        created.name = "Updated " + created.name;
        created.enabled = false;
        ResponseEntity<NotificationRouteDTO> updateResp = rest.exchange(
                base() + "/" + created.id, HttpMethod.PUT,
                new HttpEntity<>(created, headers), NotificationRouteDTO.class);
        assertEquals(HttpStatus.OK, updateResp.getStatusCode());
        NotificationRouteDTO updated = updateResp.getBody();
        assertNotNull(updated);
        assertFalse(updated.enabled);
        assertTrue(updated.name.startsWith("Updated "));
    }

    @Test
    void delete_route() {
        String token = getAdminToken();
        HttpHeaders headers = authHeaders(token);

        NotificationRouteDTO dto = emailRouteDTO();
        NotificationRouteDTO created = rest.exchange(
                base(), HttpMethod.POST, new HttpEntity<>(dto, headers), NotificationRouteDTO.class).getBody();
        assertNotNull(created);

        ResponseEntity<Void> deleteResp = rest.exchange(
                base() + "/" + created.id, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertEquals(HttpStatus.NO_CONTENT, deleteResp.getStatusCode());
    }

    @Test
    void delete_nonExistentRoute_returns404() {
        String token = getAdminToken();
        HttpHeaders headers = authHeaders(token);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/999999", HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void test_endpoint_firesWithoutError() {
        String token = getAdminToken();
        HttpHeaders headers = authHeaders(token);

        // Create route (email without real SMTP — SystemEmailSender will skip gracefully)
        NotificationRouteDTO dto = emailRouteDTO();
        NotificationRouteDTO created = rest.exchange(
                base(), HttpMethod.POST, new HttpEntity<>(dto, headers), NotificationRouteDTO.class).getBody();
        assertNotNull(created);

        // Test endpoint should return 200 with success body
        ResponseEntity<String> testResp = rest.exchange(
                base() + "/test/" + created.id, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals(HttpStatus.OK, testResp.getStatusCode());
        assertNotNull(testResp.getBody());
        assertTrue(testResp.getBody().contains("success") || testResp.getBody().contains("channel"),
                "Response should contain success or channel info");
    }
}
