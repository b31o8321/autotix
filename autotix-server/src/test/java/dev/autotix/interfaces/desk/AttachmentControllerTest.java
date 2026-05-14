package dev.autotix.interfaces.desk;

import dev.autotix.interfaces.auth.dto.LoginRequest;
import dev.autotix.interfaces.auth.dto.LoginResponse;
import dev.autotix.interfaces.desk.dto.AttachmentDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AttachmentController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttachmentControllerTest {

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
    void upload_withNoToken_returns401() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("hello".getBytes()) {
            @Override
            public String getFilename() { return "test.txt"; }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> resp = rest.exchange(
                base() + "/api/desk/tickets/1/attachments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void upload_withAdminToken_returns200_withDownloadUrl() {
        String token = getAdminToken();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("hello attachment".getBytes()) {
            @Override
            public String getFilename() { return "hello.txt"; }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<AttachmentDTO> resp = rest.exchange(
                base() + "/api/desk/tickets/1/attachments",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                AttachmentDTO.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        AttachmentDTO dto = resp.getBody();
        assertNotNull(dto);
        assertNotNull(dto.id);
        assertEquals("hello.txt", dto.fileName);
        assertNotNull(dto.downloadUrl);
        assertTrue(dto.downloadUrl.contains("/api/files") || dto.downloadUrl.startsWith("http"),
                "downloadUrl should be a valid URL: " + dto.downloadUrl);
        assertTrue(dto.sizeBytes > 0);
    }
}
