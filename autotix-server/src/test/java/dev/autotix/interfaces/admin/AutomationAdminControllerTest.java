package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.AutomationRuleDTO;
import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AutomationAdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AutomationAdminControllerTest {

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

    private String getAgentToken() {
        // Create an agent user first using admin credentials
        String adminToken = getAdminToken();
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        String email = "agent-automation-test-" + System.currentTimeMillis() + "@test.local";
        String createBody = "{\"email\":\"" + email + "\",\"displayName\":\"Agent\","
                + "\"password\":\"agentpw\",\"role\":\"AGENT\"}";
        rest.exchange(base() + "/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(createBody, adminHeaders), String.class);

        LoginRequest agentReq = new LoginRequest();
        agentReq.email = email;
        agentReq.password = "agentpw";
        LoginResponse agentLogin = rest.postForEntity(
                base() + "/api/auth/login", agentReq, LoginResponse.class).getBody();
        assertNotNull(agentLogin);
        return agentLogin.accessToken;
    }

    @Test
    void agentToken_returns403() {
        String agentToken = getAgentToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/admin/automation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void adminToken_postAndGet_roundTripSucceeds() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create a rule
        AutomationRuleDTO dto = new AutomationRuleDTO();
        dto.name = "admin-controller-test-" + System.currentTimeMillis();
        dto.priority = 99;
        dto.enabled = true;
        dto.conditions = Collections.emptyList();
        dto.actions = Collections.emptyList();

        ResponseEntity<AutomationRuleDTO> created = rest.exchange(
                base() + "/api/admin/automation/rules",
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                AutomationRuleDTO.class);

        assertEquals(HttpStatus.OK, created.getStatusCode());
        AutomationRuleDTO savedDto = created.getBody();
        assertNotNull(savedDto);
        assertNotNull(savedDto.id, "Created rule should have an id");

        // List rules and verify it's present
        ResponseEntity<AutomationRuleDTO[]> list = rest.exchange(
                base() + "/api/admin/automation/rules",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                AutomationRuleDTO[].class);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        AutomationRuleDTO[] rules = list.getBody();
        assertNotNull(rules);
        boolean found = false;
        for (AutomationRuleDTO r : rules) {
            if (savedDto.id.equals(r.id)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Created rule should appear in the list");
    }
}
