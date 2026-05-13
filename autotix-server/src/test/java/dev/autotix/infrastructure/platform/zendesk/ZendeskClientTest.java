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
import java.util.Collections;
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
}
