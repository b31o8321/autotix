package dev.autotix.infrastructure.platform.shopify;

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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ShopifyClient unit tests using MockWebServer.
 */
class ShopifyClientTest {

    private MockWebServer server;
    private ShopifyClient client;
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
        client = new ShopifyClient(httpClient, baseUrl);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("shop_domain", "test-store.myshopify.com");
        attrs.put("admin_api_token", "shpat_test123");
        credential = new ChannelCredential(null, null, null, attrs);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // getOrder: correct URL, method, Access-Token header
    // -----------------------------------------------------------------------

    @Test
    void getOrder_buildsCorrectUrlAndSendsAccessTokenHeader() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":123,\"order_number\":\"1001\",\"note\":\"\"}}"));

        JSONObject order = client.getOrder(credential, "123");

        assertNotNull(order);
        assertEquals(123L, order.getLongValue("id"));

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/orders/123.json"),
                "Expected path containing /orders/123.json, got: " + req.getPath());
        assertEquals("shpat_test123", req.getHeader("X-Shopify-Access-Token"),
                "Expected X-Shopify-Access-Token header");
    }

    @Test
    void getOrder_returnsNullOnNonSuccess() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"errors\":\"Not Found\"}"));

        JSONObject result = client.getOrder(credential, "9999");

        assertNull(result, "Should return null on 404");
        server.takeRequest(); // consume
    }

    // -----------------------------------------------------------------------
    // appendOrderNote: fetches, concatenates, PUTs
    // -----------------------------------------------------------------------

    @Test
    void appendOrderNote_fetchesCurrentNoteAndPutsAppended() throws InterruptedException {
        // First call: getOrder returns existing note
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":42,\"note\":\"Existing note\"}}"));
        // Second call: PUT response
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":42,\"note\":\"...updated\"}}"));

        client.appendOrderNote(credential, "42", "Hello from Autotix");

        // First request: GET
        RecordedRequest getReq = server.takeRequest();
        assertEquals("GET", getReq.getMethod());
        assertTrue(getReq.getPath().contains("/orders/42.json"));

        // Second request: PUT
        RecordedRequest putReq = server.takeRequest();
        assertEquals("PUT", putReq.getMethod());
        assertTrue(putReq.getPath().contains("/orders/42.json"));
        assertEquals("shpat_test123", putReq.getHeader("X-Shopify-Access-Token"));

        String bodyStr = putReq.getBody().readUtf8();
        JSONObject body = JSON.parseObject(bodyStr);
        assertNotNull(body.getJSONObject("order"), "PUT body should contain 'order' key");
        String note = body.getJSONObject("order").getString("note");
        assertNotNull(note);
        assertTrue(note.contains("Existing note"), "Note should contain existing note");
        assertTrue(note.contains("Hello from Autotix"), "Note should contain new reply");
        assertTrue(note.contains("Autotix reply:"), "Note should contain Autotix reply marker");
    }

    @Test
    void appendOrderNote_emptyExistingNote_doesNotLeadWithNewline() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":5,\"note\":\"\"}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":5,\"note\":\"updated\"}}"));

        client.appendOrderNote(credential, "5", "First reply");

        server.takeRequest(); // GET
        RecordedRequest putReq = server.takeRequest();
        String note = JSON.parseObject(putReq.getBody().readUtf8())
                .getJSONObject("order").getString("note");
        assertFalse(note.startsWith("\n"), "Should not lead with newline when existing note is empty");
        assertTrue(note.contains("First reply"));
    }

    @Test
    void appendOrderNote_500_throwsIntegrationException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"order\":{\"id\":7,\"note\":\"\"}}"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"errors\":\"Server Error\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.appendOrderNote(credential, "7", "reply"),
                "Non-2xx PUT should throw IntegrationException");
    }

    // -----------------------------------------------------------------------
    // ping: returns true on 200, false on 401
    // -----------------------------------------------------------------------

    @Test
    void ping_trueOn200_falseOn401() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"shop\":{\"name\":\"Test Store\"}}"));
        assertTrue(client.ping(credential));

        server.enqueue(new MockResponse().setResponseCode(401));
        assertFalse(client.ping(credential));
    }
}
