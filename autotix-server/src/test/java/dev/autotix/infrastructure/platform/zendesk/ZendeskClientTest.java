package dev.autotix.infrastructure.platform.zendesk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelCredential;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZendeskClient tests using MockWebServer.
 */
class ZendeskClientTest {

    private MockWebServer server;
    private ZendeskClient client;
    private ChannelCredential credential;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        String baseUrl = server.url("").toString().replaceAll("/$", "");
        client = new ZendeskClient(httpClient, baseUrl);

        // Credential with subdomain (used only in production path; baseUrlOverride is used here)
        credential = new ChannelCredential(
                "access-token-xyz",
                null,
                null,
                Collections.singletonMap("subdomain", "testco"));
    }

    /** Builds a credential that uses the new API-token path (email + api_token). */
    private ChannelCredential apiTokenCredential() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("subdomain", "testco");
        attrs.put("email", "agent@testco.com");
        attrs.put("api_token", "secret-token-123");
        return new ChannelCredential(null, null, null, attrs);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // postComment: correct path, method, body, Authorization header
    // -----------------------------------------------------------------------

    @Test
    void postComment_usesCorrectPathMethodBodyAndAuth() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ticket\":{\"id\":42}}"));

        client.postComment(credential, "42", "<p>Hello customer</p>");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("PUT", recorded.getMethod());
        assertTrue(recorded.getPath().contains("/api/v2/tickets/42.json"),
                "Expected path /api/v2/tickets/42.json, got: " + recorded.getPath());
        assertEquals("Bearer access-token-xyz", recorded.getHeader("Authorization"));

        String bodyStr = recorded.getBody().readUtf8();
        JSONObject body = JSON.parseObject(bodyStr);
        assertNotNull(body.getJSONObject("ticket"));
        JSONObject comment = body.getJSONObject("ticket").getJSONObject("comment");
        assertNotNull(comment, "Expected 'comment' in body");
        assertEquals("<p>Hello customer</p>", comment.getString("html_body"));
        assertTrue(comment.getBooleanValue("public"));
    }

    // -----------------------------------------------------------------------
    // updateStatus: PUT body contains status=solved
    // -----------------------------------------------------------------------

    @Test
    void updateStatus_putBodyContainsSolvedStatus() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ticket\":{\"id\":7,\"status\":\"solved\"}}"));

        client.updateStatus(credential, "7", "solved");

        RecordedRequest recorded = server.takeRequest();
        assertEquals("PUT", recorded.getMethod());
        assertTrue(recorded.getPath().contains("/api/v2/tickets/7.json"),
                "Expected path /api/v2/tickets/7.json, got: " + recorded.getPath());

        String bodyStr = recorded.getBody().readUtf8();
        JSONObject body = JSON.parseObject(bodyStr);
        JSONObject ticket = body.getJSONObject("ticket");
        assertNotNull(ticket);
        assertEquals("solved", ticket.getString("status"));
    }

    // -----------------------------------------------------------------------
    // ping: returns true on 200, false on 4xx
    // -----------------------------------------------------------------------

    @Test
    void ping_trueOn200_falseOn4xx() {
        // First call: 200
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"user\":{\"id\":1}}"));
        assertTrue(client.ping(credential), "Should return true on 200");

        // Second call: 401
        server.enqueue(new MockResponse().setResponseCode(401));
        assertFalse(client.ping(credential), "Should return false on 401");
    }

    // -----------------------------------------------------------------------
    // buildAuthHeader: legacy Bearer path vs new API-token Basic path
    // -----------------------------------------------------------------------

    @Test
    void buildAuthHeader_legacyBearerToken() {
        String header = client.buildAuthHeader(credential);
        assertEquals("Bearer access-token-xyz", header,
                "Legacy credential (accessToken, no api_token attr) should produce Bearer header");
    }

    @Test
    void buildAuthHeader_apiTokenBasicAuth() {
        ChannelCredential cred = apiTokenCredential();
        String header = client.buildAuthHeader(cred);
        // Expected: Basic base64("agent@testco.com/token:secret-token-123")
        String expectedRaw = "agent@testco.com/token:secret-token-123";
        String expectedEncoded = Base64.getEncoder().encodeToString(
                expectedRaw.getBytes(StandardCharsets.ISO_8859_1));
        assertEquals("Basic " + expectedEncoded, header,
                "API-token credential should produce HTTP Basic auth with email/token:{api_token}");
    }

    @Test
    void postComment_withApiToken_usesBasicAuth() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ticket\":{\"id\":99}}"));

        ChannelCredential cred = apiTokenCredential();
        client.postComment(cred, "99", "<p>reply</p>");

        RecordedRequest recorded = server.takeRequest();
        String authHeader = recorded.getHeader("Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "),
                "API-token postComment should use Basic auth, got: " + authHeader);
        // Decode and verify the username part contains "/token"
        String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)),
                StandardCharsets.ISO_8859_1);
        assertTrue(decoded.contains("/token:"),
                "Decoded Basic auth should contain '/token:', got: " + decoded);
        assertTrue(decoded.startsWith("agent@testco.com"),
                "Decoded Basic auth username should be email, got: " + decoded);
    }
}
