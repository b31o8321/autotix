package dev.autotix.infrastructure.platform.freshdesk;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FreshdeskClient unit tests using MockWebServer.
 */
class FreshdeskClientTest {

    private MockWebServer server;
    private FreshdeskClient client;
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
        client = new FreshdeskClient(httpClient, baseUrl);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("domain", "acme");
        attrs.put("api_key", "testApiKey123");
        credential = new ChannelCredential(null, null, null, attrs);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // getTicket: correct URL + Basic auth header
    // -----------------------------------------------------------------------

    @Test
    void getTicket_buildsCorrectUrlAndBasicAuthHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":42,\"subject\":\"Test ticket\",\"status\":2}"));

        JSONObject ticket = client.getTicket(credential, "42");

        assertNotNull(ticket);
        assertEquals(42L, ticket.getLongValue("id"));
        assertEquals("Test ticket", ticket.getString("subject"));

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/tickets/42"),
                "Expected path containing /tickets/42, got: " + req.getPath());

        // Freshdesk Basic auth: base64(api_key:X)
        String authHeader = req.getHeader("Authorization");
        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Basic "), "Should use Basic auth");
        // Decode and verify
        String decoded = new String(java.util.Base64.getDecoder()
                .decode(authHeader.substring("Basic ".length())));
        assertEquals("testApiKey123:X", decoded, "Basic auth should be api_key:X");
    }

    @Test
    void getTicket_returnsNullOn404() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"description\":\"Not found\"}"));

        JSONObject result = client.getTicket(credential, "9999");

        assertNull(result, "Should return null on 404");
        server.takeRequest();
    }

    @Test
    void getTicket_throwsIntegrationExceptionOnError() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"description\":\"Server Error\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.getTicket(credential, "1"),
                "Non-404 error should throw IntegrationException");
    }

    // -----------------------------------------------------------------------
    // replyToTicket: correct JSON body
    // -----------------------------------------------------------------------

    @Test
    void replyToTicket_sendsCorrectJsonBody() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .setBody("{\"id\":99,\"body\":\"<p>hello</p>\"}"));

        client.replyToTicket(credential, "55", "<p>hello</p>");

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/tickets/55/reply"),
                "Path should contain /tickets/55/reply");

        String bodyStr = req.getBody().readUtf8();
        JSONObject body = com.alibaba.fastjson.JSON.parseObject(bodyStr);
        assertEquals("<p>hello</p>", body.getString("body"),
                "Request body should contain the reply text under 'body' key");
    }

    @Test
    void replyToTicket_throwsOnNonSuccess() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"description\":\"Forbidden\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.replyToTicket(credential, "1", "reply"),
                "Non-2xx response should throw IntegrationException");
    }

    // -----------------------------------------------------------------------
    // updateTicketStatus: correct JSON body with status code
    // -----------------------------------------------------------------------

    @Test
    void updateTicketStatus_resolved_sendsStatusCode4() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":10,\"status\":4}"));

        client.updateTicketStatus(credential, "10", 4);

        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertTrue(req.getPath().contains("/tickets/10"),
                "Path should contain /tickets/10");

        JSONObject body = com.alibaba.fastjson.JSON.parseObject(req.getBody().readUtf8());
        assertEquals(4, body.getIntValue("status"), "Should send status=4 for Resolved");
    }

    @Test
    void updateTicketStatus_closed_sendsStatusCode5() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":11,\"status\":5}"));

        client.updateTicketStatus(credential, "11", 5);

        RecordedRequest req = server.takeRequest();
        JSONObject body = com.alibaba.fastjson.JSON.parseObject(req.getBody().readUtf8());
        assertEquals(5, body.getIntValue("status"), "Should send status=5 for Closed");
    }

    // -----------------------------------------------------------------------
    // ping: returns true on 200, false on 401
    // -----------------------------------------------------------------------

    @Test
    void ping_trueOn200() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":1,\"email\":\"agent@acme.com\"}"));

        assertTrue(client.ping(credential), "ping should return true on 200");
    }

    @Test
    void ping_falseOn401() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertFalse(client.ping(credential), "ping should return false on 401");
    }

    // -----------------------------------------------------------------------
    // buildUrl: domain normalisation
    // -----------------------------------------------------------------------

    @Test
    void buildUrl_acceptsFullDomain() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("domain", "acme.freshdesk.com");
        attrs.put("api_key", "k");
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);

        // Use a fresh client without baseUrlOverride so it builds the real URL
        FreshdeskClient prod = new FreshdeskClient();
        String url = prod.buildUrl(cred, "/tickets/1");
        assertEquals("https://acme.freshdesk.com/api/v2/tickets/1", url);
    }

    @Test
    void buildUrl_acceptsSubdomainOnly() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("domain", "mycompany");
        attrs.put("api_key", "k");
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);

        FreshdeskClient prod = new FreshdeskClient();
        String url = prod.buildUrl(cred, "/agents/me");
        assertEquals("https://mycompany.freshdesk.com/api/v2/agents/me", url);
    }
}
