package dev.autotix.infrastructure.platform.line;

import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LineClient unit tests using MockWebServer.
 */
class LineClientTest {

    private MockWebServer server;
    private LineClient client;
    private LineCredentials credentials;

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
        client = new LineClient(httpClient, baseUrl);

        // Build credentials directly via reflective-style helper
        credentials = buildCredentials("test-access-token", "test-secret");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // pushText
    // -----------------------------------------------------------------------

    @Test
    void pushText_sendsCorrectJsonAndBearerToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

        client.pushText("my-access-token", "Uabc123", "Hello from Autotix");

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/v2/bot/message/push"));
        assertEquals("Bearer my-access-token", req.getHeader("Authorization"));

        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"to\":\"Uabc123\""), "body should contain target userId");
        assertTrue(body.contains("\"text\":\"Hello from Autotix\""), "body should contain message text");
        assertTrue(body.contains("\"type\":\"text\""), "message type should be 'text'");
    }

    @Test
    void pushText_throwsIntegrationExceptionOn4xx() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"message\":\"bad request\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.pushText("tok", "Uabc", "hi"));
    }

    // -----------------------------------------------------------------------
    // getProfile
    // -----------------------------------------------------------------------

    @Test
    void getProfile_parsesDisplayName() throws Exception {
        String profileJson = "{\"userId\":\"U123\",\"displayName\":\"Alice\",\"pictureUrl\":\"https://profile.example.com/pic.jpg\"}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(profileJson));

        JSONObject profile = client.getProfile("tok", "U123");

        assertNotNull(profile);
        assertEquals("Alice", profile.getString("displayName"));
        assertEquals("U123", profile.getString("userId"));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().endsWith("/v2/bot/profile/U123"));
        assertEquals("Bearer tok", req.getHeader("Authorization"));
    }

    @Test
    void getProfile_throwsIntegrationExceptionOn404() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"not found\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.getProfile("tok", "Uunknown"));
    }

    // -----------------------------------------------------------------------
    // ping
    // -----------------------------------------------------------------------

    @Test
    void ping_returnsTrueOn200() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"userId\":\"U000\",\"basicId\":\"@autotix\",\"displayName\":\"AutotixBot\"}"));

        assertTrue(client.ping(credentials));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().endsWith("/v2/bot/info"));
        assertEquals("Bearer test-access-token", req.getHeader("Authorization"));
    }

    @Test
    void ping_throwsAuthExceptionOn401() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"message\":\"invalid token\"}"));

        assertThrows(AutotixException.AuthException.class, () -> client.ping(credentials));
    }

    @Test
    void ping_throwsAuthExceptionOn403() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"forbidden\"}"));

        assertThrows(AutotixException.AuthException.class, () -> client.ping(credentials));
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static LineCredentials buildCredentials(String accessToken, String secret) {
        // LineCredentials has a private constructor; use its public factory
        // but we need a ChannelCredential — build a minimal mock
        java.util.Map<String, String> attrs = new java.util.HashMap<>();
        attrs.put("channel_access_token", accessToken);
        attrs.put("channel_secret", secret);
        dev.autotix.domain.channel.ChannelCredential cred =
                new dev.autotix.domain.channel.ChannelCredential(null, null, null, attrs);
        return LineCredentials.from(cred);
    }
}
