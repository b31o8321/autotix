package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.CustomFieldDTO;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CustomFieldAdminController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CustomFieldAdminControllerTest {

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
    void crudRoundTrip() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String uniqueKey = "cf-key-" + System.currentTimeMillis();

        // POST — create
        CustomFieldDTO createReq = new CustomFieldDTO();
        createReq.name = "Test Field";
        createReq.key = uniqueKey;
        createReq.type = "TEXT";
        createReq.appliesTo = "TICKET";
        createReq.required = false;
        createReq.displayOrder = 10;

        ResponseEntity<CustomFieldDTO> created = rest.exchange(
                base() + "/api/admin/custom-fields",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                CustomFieldDTO.class);

        assertEquals(HttpStatus.OK, created.getStatusCode());
        CustomFieldDTO savedField = created.getBody();
        assertNotNull(savedField);
        assertNotNull(savedField.id);
        assertEquals(uniqueKey, savedField.key);
        assertEquals("TICKET", savedField.appliesTo);
        assertEquals("TEXT", savedField.type);

        // GET?appliesTo=TICKET — should contain our field
        ResponseEntity<CustomFieldDTO[]> list = rest.exchange(
                base() + "/api/admin/custom-fields?appliesTo=TICKET",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CustomFieldDTO[].class);

        assertEquals(HttpStatus.OK, list.getStatusCode());
        CustomFieldDTO[] fields = list.getBody();
        assertNotNull(fields);
        boolean found = Arrays.stream(fields).anyMatch(f -> savedField.id.equals(f.id));
        assertTrue(found, "Created field should appear in TICKET list");

        // GET?appliesTo=CUSTOMER — should NOT contain our TICKET field
        ResponseEntity<CustomFieldDTO[]> customerList = rest.exchange(
                base() + "/api/admin/custom-fields?appliesTo=CUSTOMER",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CustomFieldDTO[].class);

        assertEquals(HttpStatus.OK, customerList.getStatusCode());
        CustomFieldDTO[] customerFields = customerList.getBody();
        assertNotNull(customerFields);
        boolean notFoundInCustomer = Arrays.stream(customerFields)
                .noneMatch(f -> savedField.id.equals(f.id));
        assertTrue(notFoundInCustomer, "TICKET field should NOT appear in CUSTOMER list");

        // PUT — update name/required/displayOrder
        CustomFieldDTO updateReq = new CustomFieldDTO();
        updateReq.name = "Updated Field Name";
        updateReq.required = true;
        updateReq.displayOrder = 20;

        ResponseEntity<CustomFieldDTO> updated = rest.exchange(
                base() + "/api/admin/custom-fields/" + savedField.id,
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                CustomFieldDTO.class);

        assertEquals(HttpStatus.OK, updated.getStatusCode());
        CustomFieldDTO updatedField = updated.getBody();
        assertNotNull(updatedField);
        assertEquals("Updated Field Name", updatedField.name);
        assertTrue(updatedField.required);
        assertEquals(20, updatedField.displayOrder);
        // key and type should be immutable
        assertEquals(uniqueKey, updatedField.key);
        assertEquals("TEXT", updatedField.type);

        // DELETE
        ResponseEntity<Void> deleted = rest.exchange(
                base() + "/api/admin/custom-fields/" + savedField.id,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatusCode());
    }

    @Test
    void duplicateKey_returns409() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String uniqueKey = "cf-dupe-" + System.currentTimeMillis();

        CustomFieldDTO createReq = new CustomFieldDTO();
        createReq.name = "First Field";
        createReq.key = uniqueKey;
        createReq.type = "TEXT";
        createReq.appliesTo = "TICKET";

        ResponseEntity<CustomFieldDTO> first = rest.exchange(
                base() + "/api/admin/custom-fields",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                CustomFieldDTO.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        createReq.name = "Second Field";
        ResponseEntity<String> second = rest.exchange(
                base() + "/api/admin/custom-fields",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                String.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }
}
