package dev.autotix.infrastructure.platform.telegram;

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
 * TelegramClient unit tests using MockWebServer.
 */
class TelegramClientTest {

    private MockWebServer server;
    private TelegramClient client;
    private static final String BOT_TOKEN = "123456:TestBotToken";

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
        client = new TelegramClient(httpClient, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // sendMessage: posts correct JSON
    // -----------------------------------------------------------------------

    @Test
    void sendMessage_postsCorrectJson() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":{\"message_id\":1}}"));

        client.sendMessage(BOT_TOKEN, "12345678", "Hello Telegram!");

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/bot" + BOT_TOKEN + "/sendMessage"),
                "Path should contain /bot{token}/sendMessage");

        String body = req.getBody().readUtf8();
        JSONObject json = com.alibaba.fastjson.JSON.parseObject(body);
        assertEquals("12345678", json.getString("chat_id"));
        assertEquals("Hello Telegram!", json.getString("text"));
        assertEquals("HTML", json.getString("parse_mode"));
    }

    @Test
    void sendMessage_throwsOnNonSuccess() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"ok\":false,\"description\":\"Bad Request\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.sendMessage(BOT_TOKEN, "99", "hi"),
                "Non-2xx response should throw IntegrationException");
    }

    // -----------------------------------------------------------------------
    // getMe: parses bot info response
    // -----------------------------------------------------------------------

    @Test
    void getMe_parsesBotInfo() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":{\"id\":123456,\"username\":\"TestBot\",\"first_name\":\"Test\"}}"));

        JSONObject result = client.getMe(BOT_TOKEN);

        assertNotNull(result);
        assertEquals(123456L, result.getLongValue("id"));
        assertEquals("TestBot", result.getString("username"));

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertTrue(req.getPath().contains("/getMe"));
    }

    @Test
    void getMe_throwsOnUnauthorized() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"ok\":false,\"description\":\"Unauthorized\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.getMe(BOT_TOKEN),
                "401 should throw IntegrationException");
    }

    // -----------------------------------------------------------------------
    // setWebhook: posts URL and secret
    // -----------------------------------------------------------------------

    @Test
    void setWebhook_postsUrlAndSecret() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":true,\"description\":\"Webhook was set\"}"));

        client.setWebhook(BOT_TOKEN, "https://autotix.example.com/v2/webhook/TELEGRAM/abc123", "mySecret");

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/setWebhook"));

        JSONObject body = com.alibaba.fastjson.JSON.parseObject(req.getBody().readUtf8());
        assertEquals("https://autotix.example.com/v2/webhook/TELEGRAM/abc123", body.getString("url"));
        assertEquals("mySecret", body.getString("secret_token"));
    }

    @Test
    void setWebhook_omitsSecretWhenNull() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":true}"));

        client.setWebhook(BOT_TOKEN, "https://example.com/webhook", null);

        RecordedRequest req = server.takeRequest();
        JSONObject body = com.alibaba.fastjson.JSON.parseObject(req.getBody().readUtf8());
        assertNull(body.getString("secret_token"), "secret_token should be absent when null");
    }

    // -----------------------------------------------------------------------
    // ping: true on ok, false on error
    // -----------------------------------------------------------------------

    @Test
    void ping_trueOnOk() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"ok\":true,\"result\":{\"id\":1,\"username\":\"TestBot\"}}"));

        assertTrue(client.ping(BOT_TOKEN), "ping should return true when getMe succeeds");
    }

    @Test
    void ping_falseOn401() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"ok\":false,\"description\":\"Unauthorized\"}"));

        assertFalse(client.ping(BOT_TOKEN), "ping should return false when getMe returns 401");
    }

    // -----------------------------------------------------------------------
    // buildUrl: correct URL construction
    // -----------------------------------------------------------------------

    @Test
    void buildUrl_constructsCorrectUrl() {
        TelegramClient prod = new TelegramClient();
        String url = prod.buildUrl("123:abc", "sendMessage");
        assertEquals("https://api.telegram.org/bot123:abc/sendMessage", url);
    }
}
