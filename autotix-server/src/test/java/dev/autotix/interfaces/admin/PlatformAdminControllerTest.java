package dev.autotix.interfaces.admin;

import dev.autotix.interfaces.admin.dto.PlatformDescriptorDTO;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GET /api/admin/platforms.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlatformAdminControllerTest {

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
    void list_withAdminToken_returnsAllPlatforms() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<PlatformDescriptorDTO[]> resp = rest.exchange(
                base() + "/api/admin/platforms",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PlatformDescriptorDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        PlatformDescriptorDTO[] platforms = resp.getBody();
        assertNotNull(platforms);
        assertTrue(platforms.length > 0, "Should return at least one platform");

        // CUSTOM platform should have authMethod=NONE
        Optional<PlatformDescriptorDTO> custom = Arrays.stream(platforms)
                .filter(p -> "CUSTOM".equals(p.platform))
                .findFirst();
        assertTrue(custom.isPresent(), "CUSTOM platform should be present");
        assertEquals("NONE", custom.get().authMethod, "CUSTOM should have authMethod=NONE");
        assertTrue(custom.get().functional, "CUSTOM should be functional=true");
        assertNotNull(custom.get().allowedChannelTypes);
        assertTrue(custom.get().allowedChannelTypes.size() >= 2,
                "CUSTOM should allow at least CHAT and EMAIL");

        // EMAIL platform should have authMethod=EMAIL_BASIC
        Optional<PlatformDescriptorDTO> email = Arrays.stream(platforms)
                .filter(p -> "EMAIL".equals(p.platform))
                .findFirst();
        assertTrue(email.isPresent(), "EMAIL platform should be present");
        assertEquals("EMAIL_BASIC", email.get().authMethod, "EMAIL should have authMethod=EMAIL_BASIC");
        assertTrue(email.get().functional, "EMAIL should be functional=true");
        assertEquals(11, email.get().authFields.size(),
                "EMAIL should declare 11 auth fields (imap+smtp+from_address)");
    }

    @Test
    void list_withoutToken_returns401Or403() {
        ResponseEntity<String> resp = rest.getForEntity(
                base() + "/api/admin/platforms", String.class);

        assertTrue(
                resp.getStatusCode() == HttpStatus.UNAUTHORIZED
                        || resp.getStatusCode() == HttpStatus.FORBIDDEN,
                "Unauthenticated request should be rejected, got: " + resp.getStatusCode());
    }

    @Test
    void zendesk_hasApiKeyAuthAndExpectedFields() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<PlatformDescriptorDTO[]> resp = rest.exchange(
                base() + "/api/admin/platforms",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PlatformDescriptorDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        PlatformDescriptorDTO[] platforms = resp.getBody();
        assertNotNull(platforms);

        PlatformDescriptorDTO zendesk = Arrays.stream(platforms)
                .filter(p -> "ZENDESK".equals(p.platform))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ZENDESK not found"));

        assertEquals("API_KEY", zendesk.authMethod, "ZENDESK should use API_KEY auth");
        assertNotNull(zendesk.authFields);

        boolean hasSubdomain = zendesk.authFields.stream().anyMatch(f -> "subdomain".equals(f.key));
        boolean hasEmail = zendesk.authFields.stream().anyMatch(f -> "email".equals(f.key));
        boolean hasApiToken = zendesk.authFields.stream().anyMatch(f -> "api_token".equals(f.key));
        assertTrue(hasSubdomain, "ZENDESK authFields should contain 'subdomain'");
        assertTrue(hasEmail, "ZENDESK authFields should contain 'email'");
        assertTrue(hasApiToken, "ZENDESK authFields should contain 'api_token'");

        assertNotNull(zendesk.setupGuide, "ZENDESK should have a non-null setupGuide");
        assertFalse(zendesk.setupGuide.isEmpty(), "ZENDESK setupGuide should be non-empty");
    }

    @Test
    void functionalPlatforms_haveNonEmptySetupGuide() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<PlatformDescriptorDTO[]> resp = rest.exchange(
                base() + "/api/admin/platforms",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PlatformDescriptorDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        PlatformDescriptorDTO[] platforms = resp.getBody();
        assertNotNull(platforms);

        // CUSTOM and EMAIL are exempt from the setup guide requirement
        for (PlatformDescriptorDTO p : platforms) {
            if (p.functional && !"CUSTOM".equals(p.platform) && !"EMAIL".equals(p.platform)) {
                assertNotNull(p.setupGuide,
                        "Functional platform " + p.platform + " should have a setupGuide");
                assertFalse(p.setupGuide.isEmpty(),
                        "Functional platform " + p.platform + " setupGuide should not be empty");
            }
        }
    }

    @Test
    void list_functionalPlatformsAppearFirst() {
        String adminToken = getAdminToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        ResponseEntity<PlatformDescriptorDTO[]> resp = rest.exchange(
                base() + "/api/admin/platforms",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                PlatformDescriptorDTO[].class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        PlatformDescriptorDTO[] platforms = resp.getBody();
        assertNotNull(platforms);

        // All functional ones should come before any non-functional
        boolean seenNonFunctional = false;
        for (PlatformDescriptorDTO p : platforms) {
            if (!p.functional) {
                seenNonFunctional = true;
            } else if (seenNonFunctional) {
                fail("Functional platform " + p.platform + " appeared after a non-functional one");
            }
        }
    }
}
