package dev.autotix.interfaces.desk;

import dev.autotix.interfaces.admin.dto.MacroDTO;
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
 * Integration tests for MacroController (desk endpoints).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MacroControllerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    private String base() { return "http://localhost:" + port; }

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
        String adminToken = getAdminToken();
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        String email = "agent-desk-macro-" + System.currentTimeMillis() + "@test.local";
        String createBody = "{\"email\":\"" + email + "\",\"displayName\":\"DeskAgent\","
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

    private MacroDTO createMacro(String name, String body, String availableTo, String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        MacroDTO req = new MacroDTO();
        req.name = name;
        req.bodyMarkdown = body;
        req.availableTo = availableTo;

        ResponseEntity<MacroDTO> resp = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                MacroDTO.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return resp.getBody();
    }

    @Test
    void agentSeesAgentAndAiMacros_butNotAdminOnly() {
        String adminToken = getAdminToken();
        String ts = String.valueOf(System.currentTimeMillis());

        MacroDTO agentMacro = createMacro("desk-agent-" + ts, "body", "AGENT", adminToken);
        MacroDTO aiMacro = createMacro("desk-ai-" + ts, "body", "AI", adminToken);
        MacroDTO adminOnly = createMacro("desk-adminonly-" + ts, "body", "ADMIN_ONLY", adminToken);

        String agentToken = getAgentToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<MacroDTO[]> resp = rest.exchange(
                base() + "/api/desk/macros",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MacroDTO[].class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        MacroDTO[] macros = resp.getBody();
        assertNotNull(macros);

        boolean seesAgent = false, seesAi = false, seesAdminOnly = false;
        for (MacroDTO m : macros) {
            if (m.id.equals(agentMacro.id)) seesAgent = true;
            if (m.id.equals(aiMacro.id)) seesAi = true;
            if (m.id.equals(adminOnly.id)) seesAdminOnly = true;
        }
        assertTrue(seesAgent, "Agent should see AGENT macro");
        assertTrue(seesAi, "Agent should see AI macro");
        assertFalse(seesAdminOnly, "Agent should NOT see ADMIN_ONLY macro");
    }

    @Test
    void adminSeesAll() {
        String adminToken = getAdminToken();
        String ts = String.valueOf(System.currentTimeMillis());

        MacroDTO adminOnly = createMacro("admin-sees-" + ts, "body", "ADMIN_ONLY", adminToken);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<MacroDTO[]> resp = rest.exchange(
                base() + "/api/desk/macros",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MacroDTO[].class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        MacroDTO[] macros = resp.getBody();
        assertNotNull(macros);

        boolean found = false;
        for (MacroDTO m : macros) {
            if (m.id.equals(adminOnly.id)) { found = true; break; }
        }
        assertTrue(found, "Admin should see ADMIN_ONLY macro in desk endpoint");
    }

    @Test
    void postUse_incrementsUsageCount() {
        String adminToken = getAdminToken();
        String ts = String.valueOf(System.currentTimeMillis());
        MacroDTO macro = createMacro("use-test-" + ts, "body", "AGENT", adminToken);

        String agentToken = getAgentToken();
        HttpHeaders agentHeaders = new HttpHeaders();
        agentHeaders.setBearerAuth(agentToken);

        ResponseEntity<Void> useResp = rest.exchange(
                base() + "/api/desk/macros/" + macro.id + "/use",
                HttpMethod.POST,
                new HttpEntity<>(agentHeaders),
                Void.class);
        assertEquals(HttpStatus.NO_CONTENT, useResp.getStatusCode());

        // Verify count incremented via admin list
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);

        ResponseEntity<MacroDTO[]> list = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                MacroDTO[].class);
        MacroDTO[] macros = list.getBody();
        assertNotNull(macros);
        int count = 0;
        for (MacroDTO m : macros) {
            if (m.id.equals(macro.id)) { count = m.usageCount; break; }
        }
        assertEquals(1, count, "usageCount should be 1 after one use");
    }
}
