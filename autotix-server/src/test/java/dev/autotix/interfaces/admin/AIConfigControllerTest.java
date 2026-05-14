package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.AIConfigDTO;
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
 * Integration tests for AIConfigController (admin-only).
 * Skips /test endpoint as it would call external AI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AIConfigControllerTest {

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
        assertNotNull(resp);
        return resp.accessToken;
    }

    @Test
    void get_withAdminToken_returnsMaskedApiKey() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<AIConfigDTO> resp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AIConfigDTO.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        AIConfigDTO dto = resp.getBody();
        assertNotNull(dto);
        assertNotNull(dto.apiKey, "apiKey must not be null");
        // The actual apiKey in test config is "test" (< 5 chars), so masked as "***"
        // or if >= 5 chars, as "sk-***<last4>"
        assertTrue(dto.apiKey.startsWith("sk-***") || dto.apiKey.equals("***"),
                "API key should be masked, got: " + dto.apiKey);
    }

    @Test
    void get_responseIncludesGlobalAutoReplyEnabled() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<AIConfigDTO> resp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AIConfigDTO.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        AIConfigDTO dto = resp.getBody();
        assertNotNull(dto);
        // Default should be true
        assertTrue(dto.globalAutoReplyEnabled, "globalAutoReplyEnabled should default to true");
    }

    @Test
    void put_withGlobalAutoReplyDisabled_persistsAndGetReflectsChange() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        AIConfigDTO update = new AIConfigDTO();
        update.globalAutoReplyEnabled = false;
        update.maxRetries = -1; // sentinel: negative means "don't change" in controller logic

        ResponseEntity<AIConfigDTO> putResp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                AIConfigDTO.class);

        assertEquals(HttpStatus.OK, putResp.getStatusCode());
        AIConfigDTO saved = putResp.getBody();
        assertNotNull(saved);
        assertFalse(saved.globalAutoReplyEnabled, "globalAutoReplyEnabled should now be false");

        // Verify GET reflects it
        ResponseEntity<AIConfigDTO> getResp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AIConfigDTO.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertFalse(getResp.getBody().globalAutoReplyEnabled);

        // Restore to true for other tests
        update.globalAutoReplyEnabled = true;
        rest.exchange(base() + "/api/admin/ai", HttpMethod.PUT,
                new HttpEntity<>(update, headers), AIConfigDTO.class);
    }

    @Test
    void put_withAdminToken_updatesConfigAndGetReflectsChange() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Update model and systemPrompt
        AIConfigDTO update = new AIConfigDTO();
        update.model = "updated-test-model-" + System.currentTimeMillis();
        update.systemPrompt = "Updated system prompt";

        ResponseEntity<AIConfigDTO> putResp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                AIConfigDTO.class);

        assertEquals(HttpStatus.OK, putResp.getStatusCode());

        // Verify GET reflects the update
        ResponseEntity<AIConfigDTO> getResp = rest.exchange(
                base() + "/api/admin/ai",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AIConfigDTO.class);

        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        AIConfigDTO current = getResp.getBody();
        assertNotNull(current);
        assertEquals(update.model, current.model, "model should be updated");
        assertEquals("Updated system prompt", current.systemPrompt, "systemPrompt should be updated");
    }
}
