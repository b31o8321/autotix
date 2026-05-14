package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.SlaPolicyDTO;
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
 * Integration tests for SlaPolicyAdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SlaPolicyAdminControllerTest {

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
        assertNotNull(resp, "Expected LoginResponse but got null");
        return resp.accessToken;
    }

    @Test
    void getAll_withoutAuth_returns401() {
        ResponseEntity<Object> resp = rest.exchange(
                base() + "/api/admin/sla",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Object.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void getAll_withAdminToken_returnsFourPolicies() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<SlaPolicyDTO[]> resp = rest.exchange(
                base() + "/api/admin/sla",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                SlaPolicyDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        SlaPolicyDTO[] policies = resp.getBody();
        assertNotNull(policies);
        assertEquals(4, policies.length, "Bootstrap must seed 4 policies");
    }

    @Test
    void put_withAdminToken_upsertsPolicyAndGetReflectsChange() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        SlaPolicyDTO update = new SlaPolicyDTO();
        update.name = "Updated Normal SLA";
        update.firstResponseMinutes = 180;
        update.resolutionMinutes = 900;
        update.enabled = true;

        ResponseEntity<SlaPolicyDTO> putResp = rest.exchange(
                base() + "/api/admin/sla/NORMAL",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                SlaPolicyDTO.class);

        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        SlaPolicyDTO updated = putResp.getBody();
        assertNotNull(updated);
        assertEquals("Updated Normal SLA", updated.name);
        assertEquals(180, updated.firstResponseMinutes);
        assertEquals(900, updated.resolutionMinutes);
        assertEquals("NORMAL", updated.priority);

        // Verify GET reflects change
        ResponseEntity<SlaPolicyDTO[]> getResp = rest.exchange(
                base() + "/api/admin/sla",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                SlaPolicyDTO[].class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        SlaPolicyDTO[] all = getResp.getBody();
        assertNotNull(all);
        SlaPolicyDTO normal = null;
        for (SlaPolicyDTO p : all) {
            if ("NORMAL".equals(p.priority)) {
                normal = p;
                break;
            }
        }
        assertNotNull(normal, "NORMAL policy not found in response");
        assertEquals(180, normal.firstResponseMinutes);
        assertEquals(900, normal.resolutionMinutes);
    }

    @Test
    void put_invalidPriority_returns400OrException() {
        String token = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        SlaPolicyDTO update = new SlaPolicyDTO();
        update.name = "Bad";
        update.firstResponseMinutes = 60;
        update.resolutionMinutes = 240;
        update.enabled = true;

        ResponseEntity<Object> resp = rest.exchange(
                base() + "/api/admin/sla/INVALID_PRIORITY",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                Object.class);

        assertTrue(resp.getStatusCode().is4xxClientError(),
                "Expected 4xx for invalid priority, got: " + resp.getStatusCode());
    }
}
