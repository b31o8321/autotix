package dev.autotix.interfaces.auth;

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
 * End-to-end auth tests against a running server (random port, H2 in-memory).
 * The bootstrap admin is auto-created on startup (admin@test.local / test-admin).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() {
        return "http://localhost:" + port;
    }

    @Test
    void login_bootstrapAdmin_returnsTokens() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";

        ResponseEntity<LoginResponse> resp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        LoginResponse body = resp.getBody();
        assertNotNull(body);
        assertNotNull(body.accessToken, "accessToken must not be null");
        assertNotNull(body.refreshToken, "refreshToken must not be null");
        assertTrue(body.accessExpiresAt > System.currentTimeMillis());
    }

    @Test
    void adminEndpoint_noToken_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/admin/users", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void adminEndpoint_withAdminToken_returns200() {
        // First login to get token
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse loginResp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(loginResp);

        // Call admin endpoint with token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResp.accessToken);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    void adminEndpoint_withAgentToken_returns403() {
        // First login as admin, then create an agent user
        LoginRequest adminReq = new LoginRequest();
        adminReq.email = "admin@test.local";
        adminReq.password = "test-admin";
        LoginResponse adminLogin = rest.postForEntity(
                base() + "/api/auth/login", adminReq, LoginResponse.class).getBody();
        assertNotNull(adminLogin);

        // Create an agent user
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminLogin.accessToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        String createBody = "{\"email\":\"agent-e2e@test.local\",\"displayName\":\"Agent\","
                + "\"password\":\"agentpw\",\"role\":\"AGENT\"}";
        rest.exchange(base() + "/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders), String.class);

        // Login as agent
        LoginRequest agentReq = new LoginRequest();
        agentReq.email = "agent-e2e@test.local";
        agentReq.password = "agentpw";
        LoginResponse agentLogin = rest.postForEntity(
                base() + "/api/auth/login", agentReq, LoginResponse.class).getBody();
        assertNotNull(agentLogin);

        // Try admin endpoint with agent token
        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentLogin.accessToken);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/admin/users",
                HttpMethod.GET,
                new HttpEntity<>(agentHeaders),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void me_withValidToken_returnsUserInfo() {
        LoginRequest req = new LoginRequest();
        req.email = "admin@test.local";
        req.password = "test-admin";
        LoginResponse loginResp = rest.postForEntity(
                base() + "/api/auth/login", req, LoginResponse.class).getBody();
        assertNotNull(loginResp);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResp.accessToken);
        ResponseEntity<LoginResponse.UserInfo> resp = rest.exchange(
                base() + "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                LoginResponse.UserInfo.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        LoginResponse.UserInfo info = resp.getBody();
        assertNotNull(info);
        assertEquals("ADMIN", info.role);
    }
}
