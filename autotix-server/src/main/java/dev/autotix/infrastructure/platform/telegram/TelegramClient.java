package dev.autotix.infrastructure.platform.telegram;

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
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Telegram Bot API.
 *
 * <p>Base URL: {@code https://api.telegram.org/bot{token}/{method}}
 *
 * <p>The package-private constructor accepts a pre-built {@link OkHttpClient} and
 * a base-URL override so tests can target a {@code MockWebServer}.
 */
@Component
public class TelegramClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final String TELEGRAM_API_BASE = "https://api.telegram.org";

    final OkHttpClient httpClient;
    /** Null in production; set in tests to point at MockWebServer. */
    private final String baseUrlOverride;

    /** Production constructor — uses a dedicated OkHttpClient. */
    public TelegramClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrlOverride = null;
    }

    /** Package-private constructor for tests. */
    TelegramClient(OkHttpClient httpClient, String baseUrlOverride) {
        this.httpClient = httpClient;
        this.baseUrlOverride = baseUrlOverride;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Send a message to a Telegram chat.
     *
     * @param botToken the bot token from BotFather
     * @param chatId   the Telegram chat_id (stored in ticket.externalThreadId)
     * @param text     message text (may include HTML tags if parse_mode=HTML)
     */
    public void sendMessage(String botToken, String chatId, String text) {
        JSONObject body = new JSONObject();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "HTML");

        Request request = new Request.Builder()
                .url(buildUrl(botToken, "sendMessage"))
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "sendMessage chatId=" + chatId);
    }

    /**
     * Call getMe to retrieve bot info — used as a connectivity/credential check.
     *
     * @param botToken the bot token
     * @return parsed JSON result object from Telegram (e.g. {@code {id, username, ...}})
     */
    public JSONObject getMe(String botToken) {
        Request request = new Request.Builder()
                .url(buildUrl(botToken, "getMe"))
                .get()
                .build();

        try {
            Response response = httpClient.newCall(request).execute();
            try {
                String responseBody = response.body() != null ? response.body().string() : "{}";
                if (!response.isSuccessful()) {
                    throw new AutotixException.IntegrationException(
                            "telegram", "HTTP " + response.code() + " calling getMe: " + responseBody);
                }
                JSONObject parsed = JSON.parseObject(responseBody);
                if (parsed == null || !Boolean.TRUE.equals(parsed.getBoolean("ok"))) {
                    throw new AutotixException.IntegrationException(
                            "telegram", "getMe returned ok=false: " + responseBody);
                }
                return parsed.getJSONObject("result");
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException("telegram", "Network error calling getMe", e);
        }
    }

    /**
     * Register a webhook URL with Telegram. Telegram will POST all updates to this URL.
     *
     * @param botToken    the bot token
     * @param url         the public HTTPS URL Telegram should POST updates to
     * @param secretToken optional (1-256 chars A-Za-z0-9_-); if non-null, Telegram sends it in
     *                    {@code X-Telegram-Bot-Api-Secret-Token} header on every update
     */
    public void setWebhook(String botToken, String url, String secretToken) {
        JSONObject body = new JSONObject();
        body.put("url", url);
        if (secretToken != null && !secretToken.isEmpty()) {
            body.put("secret_token", secretToken);
        }

        Request request = new Request.Builder()
                .url(buildUrl(botToken, "setWebhook"))
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "setWebhook url=" + url);
    }

    /**
     * Verify that the credentials are valid by calling getMe.
     *
     * @return true if getMe returns ok=true; false or throws on any error
     */
    public boolean ping(String botToken) {
        try {
            JSONObject result = getMe(botToken);
            return result != null;
        } catch (AutotixException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    String buildUrl(String botToken, String method) {
        String base = baseUrlOverride != null ? baseUrlOverride : TELEGRAM_API_BASE;
        return base + "/bot" + botToken + "/" + method;
    }

    private void execute(Request request, String context) {
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    String body = "";
                    try {
                        if (response.body() != null) body = response.body().string();
                    } catch (IOException ignored) {}
                    throw new AutotixException.IntegrationException(
                            "telegram", "HTTP " + response.code() + " for " + context + ": " + body);
                }
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException("telegram", "Network error for " + context, e);
        }
    }
}
