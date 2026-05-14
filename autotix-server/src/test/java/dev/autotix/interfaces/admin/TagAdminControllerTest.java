package dev.autotix.interfaces.admin;

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
 * Integration tests for TagAdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TagAdminControllerTest {

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

        String email = "agent-tag-test-" + System.currentTimeMillis() + "@test.local";
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
    void nonAdmin_returns403() {
        String agentToken = getAgentToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(agentToken);

        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/admin/tags",
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
        TagDTO createReq = new TagDTO();
        createReq.name = "tag-test-" + System.currentTimeMillis();
        createReq.color = "#FF0000";
        createReq.category = "test";

        ResponseEntity<TagDTO> created = rest.exchange(
                base() + "/api/admin/tags",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                TagDTO.class);

        assertEquals(HttpStatus.OK, created.getStatusCode());
        TagDTO savedTag = created.getBody();
        assertNotNull(savedTag);
        assertNotNull(savedTag.id, "Created tag should have an id");
        assertEquals(createReq.name, savedTag.name);
        assertEquals("#FF0000", savedTag.color);

        // GET — verify it's in the list
        ResponseEntity<TagDTO[]> list = rest.exchange(
                base() + "/api/admin/tags",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                TagDTO[].class);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        TagDTO[] tags = list.getBody();
        assertNotNull(tags);
        boolean found = false;
        for (TagDTO t : tags) {
            if (savedTag.id.equals(t.id)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Created tag should appear in the list");

        // PUT — update color/category
        TagDTO updateReq = new TagDTO();
        updateReq.color = "#00FF00";
        updateReq.category = "updated";

        ResponseEntity<TagDTO> updated = rest.exchange(
                base() + "/api/admin/tags/" + savedTag.id,
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                TagDTO.class);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        TagDTO updatedTag = updated.getBody();
        assertNotNull(updatedTag);
        assertEquals("#00FF00", updatedTag.color);
        assertEquals("updated", updatedTag.category);
        assertEquals(savedTag.name, updatedTag.name, "Name should be immutable");

        // DELETE
        ResponseEntity<Void> deleted = rest.exchange(
                base() + "/api/admin/tags/" + savedTag.id,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
    }

    @Test
    void duplicateName_returns409() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String name = "dupe-tag-" + System.currentTimeMillis();
        TagDTO createReq = new TagDTO();
        createReq.name = name;

        // First create should succeed
        ResponseEntity<TagDTO> first = rest.exchange(
                base() + "/api/admin/tags",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                TagDTO.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        // Second create with same name should be 409
        ResponseEntity<String> second = rest.exchange(
                base() + "/api/admin/tags",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }
}
