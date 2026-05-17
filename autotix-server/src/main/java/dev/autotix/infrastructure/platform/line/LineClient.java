package dev.autotix.infrastructure.platform.line;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the LINE Messaging API.
 *
 * <p>Auth: {@code Authorization: Bearer <channel_access_token>} on every call.
 *
 * <p>Base URL: {@code https://api.line.me} (overridable in tests via constructor).
 */
@Component
public class LineClient {

    static final String LINE_API_BASE = "https://api.line.me";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;

    final OkHttpClient httpClient;
    private final String baseUrl;

    /** Production constructor — uses a fresh OkHttpClient. */
    public LineClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrl = LINE_API_BASE;
    }

    /** Test constructor — accepts a pre-built client and a base-URL override. */
    LineClient(OkHttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Push a plain-text message to a LINE user.
     *
     * @param accessToken LINE channel access token
     * @param toUserId    LINE userId of the recipient (e.g. {@code Uxxxxxxxxxx})
     * @param text        message text
     */
    public void pushText(String accessToken, String toUserId, String text) {
        JSONObject body = new JSONObject();
        body.put("to", toUserId);
        JSONObject msg = new JSONObject();
        msg.put("type", "text");
        msg.put("text", text);
        body.put("messages", Arrays.asList(msg));

        Request request = new Request.Builder()
                .url(baseUrl + "/v2/bot/message/push")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_TYPE))
                .build();

        execute(request, "pushText to=" + toUserId);
    }

    /**
     * Fetch a LINE user profile.
     *
     * @param accessToken LINE channel access token
     * @param userId      LINE userId
     * @return parsed JSON with {@code displayName}, {@code userId}, {@code pictureUrl}
     */
    public JSONObject getProfile(String accessToken, String userId) {
        Request request = new Request.Builder()
                .url(baseUrl + "/v2/bot/profile/" + userId)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    String rb = response.body() != null ? response.body().string() : "";
                    throw new AutotixException.IntegrationException(
                            "line", "HTTP " + response.code() + " fetching profile for " + userId + ": " + rb);
                }
                String rb = response.body() != null ? response.body().string() : "{}";
                return JSON.parseObject(rb);
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "line", "Network error fetching LINE profile for " + userId, e);
        }
    }

    /**
     * Verify credentials by calling {@code GET /v2/bot/info}.
     *
     * @return {@code true} on HTTP 200; throws {@link AutotixException.AuthException} on 401/403
     */
    public boolean ping(LineCredentials credentials) {
        Request request = new Request.Builder()
                .url(baseUrl + "/v2/bot/info")
                .header("Authorization", "Bearer " + credentials.channelAccessToken)
                .get()
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                int code = response.code();
                if (code == 401 || code == 403) {
                    throw new AutotixException.AuthException(
                            "LINE credentials rejected (HTTP " + code + ")");
                }
                return response.isSuccessful();
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "line", "Network error pinging LINE bot info", e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void execute(Request request, String context) {
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    String rb = "";
                    try {
                        if (response.body() != null) rb = response.body().string();
                    } catch (IOException ignored) {}
                    throw new AutotixException.IntegrationException(
                            "line", "HTTP " + response.code() + " for " + context + ": " + rb);
                }
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "line", "Network error for " + context, e);
        }
    }
}
