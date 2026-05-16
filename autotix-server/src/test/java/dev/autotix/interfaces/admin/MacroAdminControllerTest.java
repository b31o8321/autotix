package dev.autotix.interfaces.admin;

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
 * Integration tests for MacroAdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MacroAdminControllerTest {

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
        String adminToken = getAdminToken();
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        String email = "agent-macro-" + System.currentTimeMillis() + "@test.local";
        String createBody = "{\"email\":\"" + email + "\",\"displayName\":\"MacroAgent\","
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
    void nonAdmin_returns403() {
        String agentToken = getAgentToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    }

    @Test
    void crudRoundTrip() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // POST — create
        MacroDTO createReq = new MacroDTO();
        createReq.name = "macro-test-" + System.currentTimeMillis();
        createReq.bodyMarkdown = "Thank you for contacting us.";
        createReq.category = "general";
        createReq.availableTo = "AGENT";

        ResponseEntity<MacroDTO> created = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                MacroDTO.class);

        assertEquals(HttpStatus.OK, created.getStatusCode());
        MacroDTO saved = created.getBody();
        assertNotNull(saved);
        assertNotNull(saved.id, "Created macro should have an id");
        assertEquals(createReq.name, saved.name);
        assertEquals("AGENT", saved.availableTo);

        // GET — list should contain it
        ResponseEntity<MacroDTO[]> list = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MacroDTO[].class);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        MacroDTO[] macros = list.getBody();
        assertNotNull(macros);
        boolean found = false;
        for (MacroDTO m : macros) {
            if (saved.id.equals(m.id)) { found = true; break; }
        }
        assertTrue(found, "Created macro should appear in the list");

        // PUT — update
        MacroDTO updateReq = new MacroDTO();
        updateReq.name = saved.name;
        updateReq.bodyMarkdown = "Updated body text";
        updateReq.category = "billing";
        updateReq.availableTo = "ADMIN_ONLY";

        ResponseEntity<MacroDTO> updated = rest.exchange(
                base() + "/api/admin/macros/" + saved.id,
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                MacroDTO.class);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        MacroDTO updatedBody = updated.getBody();
        assertNotNull(updatedBody);
        assertEquals("Updated body text", updatedBody.bodyMarkdown);
        assertEquals("ADMIN_ONLY", updatedBody.availableTo);

        // DELETE
        ResponseEntity<Void> deleted = rest.exchange(
                base() + "/api/admin/macros/" + saved.id,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());

        // Verify 404 after delete
        ResponseEntity<MacroDTO> afterDelete = rest.exchange(
                base() + "/api/admin/macros/" + saved.id,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MacroDTO.class);
        // No GET by id on admin, but we can verify via list
        ResponseEntity<MacroDTO[]> listAfter = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                MacroDTO[].class);
        MacroDTO[] afterMacros = listAfter.getBody();
        assertNotNull(afterMacros);
        boolean stillFound = false;
        for (MacroDTO m : afterMacros) {
            if (saved.id.equals(m.id)) { stillFound = true; break; }
        }
        assertFalse(stillFound, "Deleted macro should not appear in list");
    }

    @Test
    void duplicateName_returns409() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String name = "dupe-macro-" + System.currentTimeMillis();
        MacroDTO req = new MacroDTO();
        req.name = name;
        req.bodyMarkdown = "body";

        ResponseEntity<MacroDTO> first = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                MacroDTO.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second = rest.exchange(
                base() + "/api/admin/macros",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }
}
