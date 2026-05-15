package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.ChannelDTO;
import dev.autotix.interfaces.admin.dto.ConnectApiKeyRequest;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChannelAdminController.
 * Tests the optional platform filter on GET /api/admin/channels.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChannelAdminControllerTest {

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

    private String connectCustomChannel(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ConnectApiKeyRequest req = new ConnectApiKeyRequest();
        req.platform = "CUSTOM";
        req.channelType = "CHAT";
        req.displayName = "Test CUSTOM channel for filter";
        req.credentials = new HashMap<>();

        ResponseEntity<Map> resp = rest.exchange(
                base() + "/api/admin/channels/connect-api-key",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                Map.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        return (String) resp.getBody().get("channelId");
    }

    @Test
    void listWithPlatformFilter_returnsOnlyMatchingChannels() {
        String adminToken = getAdminToken();
        // Connect a CUSTOM channel so there's something to filter
        String channelId = connectCustomChannel(adminToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        // Filter by CUSTOM
        ResponseEntity<ChannelDTO[]> resp = rest.exchange(
                base() + "/api/admin/channels?platform=CUSTOM",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ChannelDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ChannelDTO[] channels = resp.getBody();
        assertNotNull(channels);
        assertTrue(channels.length > 0, "Should have at least one CUSTOM channel");
        Arrays.stream(channels).forEach(c ->
                assertEquals("CUSTOM", c.platform, "All returned channels should be CUSTOM platform"));

        // Clean up
        rest.exchange(
                base() + "/api/admin/channels/" + channelId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);
    }

    @Test
    void listWithoutFilter_returnsAllChannels() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<ChannelDTO[]> resp = rest.exchange(
                base() + "/api/admin/channels",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ChannelDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        // Should succeed — may be empty or non-empty depending on other tests
    }
}
