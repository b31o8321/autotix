package dev.autotix.infrastructure.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIRequest;
import dev.autotix.domain.ai.AIResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Default AIReplyPort implementation that calls any OpenAI-compatible chat/completions endpoint.
 *
 * <p>Request format:
 * <pre>
 * POST {endpoint}/chat/completions
 * Authorization: Bearer {apiKey}
 * Content-Type: application/json
 *
 * { "model": "...", "messages": [ {role, content}, ... ] }
 * </pre>
 *
 * <p>Response parsing:
 * <ul>
 *   <li>If {@code choices[0].message.content} is a JSON object with {@code reply} field -&gt; structured</li>
 *   <li>Otherwise the whole content string is treated as the reply (action=NONE, tags=null)</li>
 * </ul>
 *
 * <p>Retry: up to {@code config.maxRetries} attempts with 200ms linear backoff on non-2xx or IOException.
 */
@Component
public class OpenAICompatibleClient implements AIReplyPort {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int RETRY_BACKOFF_MS = 200;

    private final AIConfig config;
    final OkHttpClient httpClient;
    private final String endpointOverride;

    /** Production constructor — builds a default OkHttpClient from config. */
    @Autowired
    public OpenAICompatibleClient(AIConfig config) {
        this.config = config;
        this.endpointOverride = null;
        int timeout = config.getTimeoutSeconds();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Package-private constructor for tests — accepts a pre-built OkHttpClient and optional
     * endpoint override (e.g. MockWebServer URL).
     */
    OpenAICompatibleClient(AIConfig config, OkHttpClient httpClient) {
        this.config = config;
        this.endpointOverride = null;
        this.httpClient = httpClient;
    }

    /**
     * Package-private constructor for tests — full override of endpoint.
     */
    OpenAICompatibleClient(AIConfig config, OkHttpClient httpClient, String endpointOverride) {
        this.config = config;
        this.endpointOverride = endpointOverride;
        this.httpClient = httpClient;
    }

    @Override
    public AIResponse generate(AIRequest request) {
        String endpoint = (endpointOverride != null ? endpointOverride : config.getEndpoint());
        String url = endpoint + "/chat/completions";

        String bodyJson = buildRequestBody(request);
        RequestBody requestBody = RequestBody.create(bodyJson, JSON_MEDIA_TYPE);

        Request httpRequest = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build();

        int maxRetries = config.getMaxRetries();
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep((long) RETRY_BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                Response response = httpClient.newCall(httpRequest).execute();
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        return parseResponse(responseBody);
                    } else {
                        lastException = new IOException("HTTP " + response.code());
                        // continue to retry
                    }
                } finally {
                    response.close();
                }
            } catch (IOException e) {
                lastException = e;
            }
        }

        throw new AutotixException.IntegrationException(
                "openai",
                "AI call failed after " + (maxRetries + 1) + " attempt(s)",
                lastException);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build the OpenAI messages array from the AIRequest.
     * Message order:
     *   1. system prompt
     *   2. history turns (user / assistant)
     *   3. latest user message with channel + customer context
     */
    String buildRequestBody(AIRequest request) {
        JSONObject body = new JSONObject();
        body.put("model", config.getModel());

        JSONArray messages = new JSONArray();

        // System message
        String systemPrompt = (request.systemPromptOverride() != null && !request.systemPromptOverride().isEmpty())
                ? request.systemPromptOverride()
                : config.getSystemPrompt();
        if (systemPrompt == null) {
            systemPrompt = "";
        }
        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        // History turns
        if (request.history() != null) {
            for (AIRequest.HistoryTurn turn : request.history()) {
                JSONObject histMsg = new JSONObject();
                histMsg.put("role", turn.role);
                histMsg.put("content", turn.content);
                messages.add(histMsg);
            }
        }

        // Latest user message with context
        String userContent = "Channel: " + request.channelType().name()
                + "\nCustomer: " + (request.customerName() != null ? request.customerName() : "")
                + "\n\nLatest message:\n" + request.latestMessage();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        body.put("messages", messages);
        return body.toJSONString();
    }

    /**
     * Parse the OpenAI response body into an AIResponse.
     * Tries JSON structured parse first; falls back to plain text.
     */
    AIResponse parseResponse(String responseBody) {
        JSONObject root;
        try {
            root = JSON.parseObject(responseBody);
        } catch (Exception e) {
            return new AIResponse(responseBody, AIAction.NONE, null);
        }

        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return new AIResponse("", AIAction.NONE, null);
        }

        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        if (message == null) {
            return new AIResponse("", AIAction.NONE, null);
        }
        String content = message.getString("content");
        if (content == null) {
            return new AIResponse("", AIAction.NONE, null);
        }

        // Try to parse content as structured JSON
        if (content.trim().startsWith("{")) {
            try {
                JSONObject structured = JSON.parseObject(content);
                String reply = structured.getString("reply");
                if (reply != null) {
                    String actionStr = structured.getString("action");
                    AIAction action = parseAction(actionStr);
                    List<String> tags = parseTags(structured.getJSONArray("tags"));
                    return new AIResponse(reply, action, tags);
                }
            } catch (Exception ignored) {
                // fall through to plain-text fallback
            }
        }

        // Plain-text fallback
        return new AIResponse(content, AIAction.NONE, null);
    }

    private AIAction parseAction(String actionStr) {
        if (actionStr == null) {
            return AIAction.NONE;
        }
        try {
            return AIAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AIAction.NONE;
        }
    }

    private List<String> parseTags(JSONArray tagsArray) {
        if (tagsArray == null || tagsArray.isEmpty()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < tagsArray.size(); i++) {
            tags.add(tagsArray.getString(i));
        }
        return tags;
    }
}
