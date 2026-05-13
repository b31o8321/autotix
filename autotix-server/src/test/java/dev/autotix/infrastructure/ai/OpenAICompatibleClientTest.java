package dev.autotix.infrastructure.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIRequest;
import dev.autotix.domain.ai.AIResponse;
import dev.autotix.domain.channel.ChannelType;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAICompatibleClient integration tests using MockWebServer.
 */
class OpenAICompatibleClientTest {

    private MockWebServer server;
    private OpenAICompatibleClient client;
    private AIConfig config;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        config = new AIConfig();
        config.setEndpoint(server.url("").toString().replaceAll("/$", ""));
        config.setApiKey("test-api-key");
        config.setModel("gpt-test");
        config.setSystemPrompt("You are a helpful assistant.");
        config.setMaxRetries(2);
        config.setTimeoutSeconds(5);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        client = new OpenAICompatibleClient(config, httpClient, server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // Happy path: structured JSON response
    // -----------------------------------------------------------------------

    @Test
    void happyPath_structuredJsonResponse() throws InterruptedException {
        // AI returns structured JSON with reply, action, tags
        String aiContent = "{\"reply\":\"hi\",\"action\":\"CLOSE\",\"tags\":[\"x\"]}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildOpenAIResponseBody(aiContent)));

        AIRequest request = buildRequest("Hello");
        AIResponse response = client.generate(request);

        assertEquals("hi", response.reply());
        assertEquals(AIAction.CLOSE, response.action());
        assertNotNull(response.tags());
        assertEquals(1, response.tags().size());
        assertEquals("x", response.tags().get(0));

        // Verify request structure
        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertTrue(recorded.getPath().endsWith("/chat/completions"),
                "Path should end with /chat/completions, was: " + recorded.getPath());
        assertEquals("Bearer test-api-key", recorded.getHeader("Authorization"));

        String requestBodyStr = recorded.getBody().readUtf8();
        JSONObject requestBody = JSON.parseObject(requestBodyStr);
        assertEquals("gpt-test", requestBody.getString("model"));

        JSONArray messages = requestBody.getJSONArray("messages");
        assertNotNull(messages);
        assertTrue(messages.size() >= 2, "Should have at least system + user messages");

        JSONObject systemMsg = messages.getJSONObject(0);
        assertEquals("system", systemMsg.getString("role"));
        assertNotNull(systemMsg.getString("content"));
    }

    // -----------------------------------------------------------------------
    // Plain-text fallback
    // -----------------------------------------------------------------------

    @Test
    void plainTextContent_fallsBackToNoneAction() throws InterruptedException {
        String aiContent = "Just plain text response, no JSON here.";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildOpenAIResponseBody(aiContent)));

        AIRequest request = buildRequest("Hello");
        AIResponse response = client.generate(request);

        assertEquals("Just plain text response, no JSON here.", response.reply());
        assertEquals(AIAction.NONE, response.action());
        assertNull(response.tags());
    }

    // -----------------------------------------------------------------------
    // Retry: 500 then 200
    // -----------------------------------------------------------------------

    @Test
    void server500ThenSuccess_retriesAndSucceeds() {
        String aiContent = "Retry succeeded";
        // First call fails with 500
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        // Second call succeeds
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(buildOpenAIResponseBody(aiContent)));

        AIRequest request = buildRequest("Hello after retry");
        AIResponse response = client.generate(request);

        assertEquals("Retry succeeded", response.reply());
    }

    // -----------------------------------------------------------------------
    // Exhausted retries -> IntegrationException
    // -----------------------------------------------------------------------

    @Test
    void serverAlways500_throwsIntegrationException() {
        // All 3 calls (1 + 2 retries) fail
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        AIRequest request = buildRequest("This will fail");
        assertThrows(AutotixException.IntegrationException.class, () -> client.generate(request));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static AIRequest buildRequest(String message) {
        return new AIRequest(
                ChannelType.EMAIL,
                "Test Customer",
                message,
                Collections.<AIRequest.HistoryTurn>emptyList(),
                null);
    }

    /**
     * Build a minimal OpenAI chat completion response body wrapping the given content string.
     */
    private static String buildOpenAIResponseBody(String content) {
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", content);

        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");

        JSONArray choices = new JSONArray();
        choices.add(choice);

        JSONObject root = new JSONObject();
        root.put("id", "chatcmpl-test");
        root.put("object", "chat.completion");
        root.put("choices", choices);

        return root.toJSONString();
    }
}
