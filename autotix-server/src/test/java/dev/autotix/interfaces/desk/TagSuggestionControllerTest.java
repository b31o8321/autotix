package dev.autotix.interfaces.desk;

import dev.autotix.interfaces.admin.dto.TagDTO;
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
 * Integration tests for TagSuggestionController (agent-accessible).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TagSuggestionControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    private String getAgentToken() {
        // Create agent via admin
        LoginRequest adminReq = new LoginRequest();
        adminReq.email = "admin@test.local";
        adminReq.password = "test-admin";
        LoginResponse adminLogin = rest.postForEntity(
                base() + "/api/auth/login", adminReq, LoginResponse.class).getBody();
        assertNotNull(adminLogin);
        String adminToken = adminLogin.accessToken;

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        String email = "agent-suggestion-" + System.currentTimeMillis() + "@test.local";
        String createBody = "{\"email\":\"" + email + "\",\"displayName\":\"SuggAgent\","
                + "\"password\":\"suggestpw\",\"role\":\"AGENT\"}";
        rest.exchange(base() + "/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders), String.class);

        LoginRequest agentReq = new LoginRequest();
        agentReq.email = email;
        agentReq.password = "suggestpw";
        LoginResponse agentLogin = rest.postForEntity(
                base() + "/api/auth/login", agentReq, LoginResponse.class).getBody();
        assertNotNull(agentLogin);
        return agentLogin.accessToken;
    }

    @Test
    void agentCanGetSuggestions() {
        String agentToken = getAgentToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<TagDTO[]> resp = rest.exchange(
                base() + "/api/desk/tags/suggestions",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TagDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }
}
