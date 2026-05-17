package dev.autotix.infrastructure.platform.wecom;

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
 * WecomClient unit tests using MockWebServer.
 */
class WecomClientTest {

    private MockWebServer server;
    private WecomClient client;

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
        client = new WecomClient(httpClient, baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // getAccessToken — happy path + caching
    // -----------------------------------------------------------------------

    @Test
    void getAccessToken_parsesTokenAndCaches() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"ACCESS_TOKEN_123\",\"expires_in\":7200}"));

        String token1 = client.getAccessToken("corpid1", "secret1");
        assertEquals("ACCESS_TOKEN_123", token1);

        // Second call within TTL — should NOT make another HTTP request
        String token2 = client.getAccessToken("corpid1", "secret1");
        assertEquals("ACCESS_TOKEN_123", token2);

        assertEquals(1, server.getRequestCount(), "Should only make one HTTP request (token cached)");
    }

    @Test
    void getAccessToken_throwsAuthExceptionOnErrcode() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"errcode\":40013,\"errmsg\":\"invalid corpid\",\"access_token\":\"\",\"expires_in\":0}"));

        assertThrows(AutotixException.AuthException.class,
                () -> client.getAccessToken("badcorp", "badsecret"));
    }

    @Test
    void getAccessToken_throwsAuthExceptionOnHttp4xx() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"errcode\":1,\"errmsg\":\"error\"}"));

        assertThrows(AutotixException.AuthException.class,
                () -> client.getAccessToken("corp", "secret"));
    }

    // -----------------------------------------------------------------------
    // sendText
    // -----------------------------------------------------------------------

    @Test
    void sendText_postsCorrectJson() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"errcode\":0,\"errmsg\":\"ok\"}"));

        client.sendText("ACCESS_TOKEN", "ext_user_001", "wk_kfid_001", "Hello customer!");

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().contains("/cgi-bin/kf/send_msg"));
        assertTrue(req.getPath().contains("ACCESS_TOKEN"));

        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"touser\":\"ext_user_001\""));
        assertTrue(body.contains("\"open_kfid\":\"wk_kfid_001\""));
        assertTrue(body.contains("\"msgtype\":\"text\""));
        assertTrue(body.contains("\"content\":\"Hello customer!\""));
    }

    @Test
    void sendText_throwsOnErrcode() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"errcode\":45047,\"errmsg\":\"reach the limit\"}"));

        assertThrows(AutotixException.IntegrationException.class,
                () -> client.sendText("tok", "user", "kfid", "text"));
    }

    // -----------------------------------------------------------------------
    // syncMsg
    // -----------------------------------------------------------------------

    @Test
    void syncMsg_postsVoucherAndCursor() throws Exception {
        String respBody = "{\"errcode\":0,\"errmsg\":\"ok\",\"next_cursor\":\"cursor_abc\"," +
                "\"has_more\":0,\"msg_list\":[]}";
        server.enqueue(new MockResponse().setResponseCode(200).setBody(respBody));

        JSONObject result = client.syncMsg("ACCESS_TOK", "VOUCHER_123", "wk_kfid", "prev_cursor", 1000);

        assertNotNull(result);
        assertEquals("cursor_abc", result.getString("next_cursor"));

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(req);
        assertTrue(req.getPath().contains("/cgi-bin/kf/sync_msg"));

        String body = req.getBody().readUtf8();
        assertTrue(body.contains("\"voucher\":\"VOUCHER_123\""));
        assertTrue(body.contains("\"open_kfid\":\"wk_kfid\""));
        assertTrue(body.contains("\"cursor\":\"prev_cursor\""));
        assertTrue(body.contains("\"limit\":1000"));
    }

    @Test
    void syncMsg_omitsCursorWhenEmpty() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"errcode\":0,\"errmsg\":\"ok\",\"next_cursor\":\"\",\"has_more\":0,\"msg_list\":[]}"));

        client.syncMsg("tok", "voucher", "kfid", "", 100);

        RecordedRequest req = server.takeRequest(5, TimeUnit.SECONDS);
        String body = req.getBody().readUtf8();
        assertFalse(body.contains("\"cursor\""), "cursor should not be included when empty");
    }

    // -----------------------------------------------------------------------
    // ping
    // -----------------------------------------------------------------------

    @Test
    void ping_returnsTrueWhenTokenFetchSucceeds() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"PING_TOK\",\"expires_in\":7200}"));

        assertTrue(client.ping("corp", "secret"));
    }
}
