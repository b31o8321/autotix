package dev.autotix.infrastructure.platform.freshdesk;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelCredential;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Freshdesk REST API v2.
 *
 * <p>Authentication: HTTP Basic with {@code {api_key}:X}
 * (Freshdesk convention — the literal letter X as password).
 *
 * <p>Base URL: {@code https://{domain}.freshdesk.com/api/v2}
 * where {@code domain} comes from {@code credential.attributes().get("domain")}.
 *
 * <p>The test-friendly package-private constructor accepts a pre-built {@link OkHttpClient}
 * and a base-URL override so tests can target a {@code MockWebServer}.
 */
@Component
public class FreshdeskClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    final OkHttpClient httpClient;
    private final String baseUrlOverride; // null in production; set in tests

    /** Production constructor. */
    public FreshdeskClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrlOverride = null;
    }

    /** Package-private constructor for tests. */
    FreshdeskClient(OkHttpClient httpClient, String baseUrlOverride) {
        this.httpClient = httpClient;
        this.baseUrlOverride = baseUrlOverride;
    }

    /**
     * Fetch a Freshdesk ticket by ID.
     *
     * @param credential  must have {@code attributes["domain"]} and {@code attributes["api_key"]}
     * @param ticketId    the numeric ticket ID as a string
     * @return parsed JSON object from the response body, or null on 404
     */
    public JSONObject getTicket(ChannelCredential credential, String ticketId) {
        String url = buildUrl(credential, "/tickets/" + ticketId);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .get()
                .build();

        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (response.code() == 404) return null;
                if (!response.isSuccessful()) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new AutotixException.IntegrationException(
                            "freshdesk",
                            "HTTP " + response.code() + " fetching ticket " + ticketId + ": " + body);
                }
                String body = response.body() != null ? response.body().string() : "{}";
                return JSON.parseObject(body);
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "freshdesk", "Network error fetching ticket " + ticketId, e);
        }
    }

    /**
     * Post a reply to a Freshdesk ticket.
     * Uses {@code POST /api/v2/tickets/{id}/reply} with {@code {"body": htmlBody}}.
     *
     * @param credential  channel credentials
     * @param ticketId    numeric ticket ID
     * @param htmlBody    HTML or plain-text reply body
     */
    public void replyToTicket(ChannelCredential credential, String ticketId, String htmlBody) {
        JSONObject bodyObj = new JSONObject();
        bodyObj.put("body", htmlBody);

        String url = buildUrl(credential, "/tickets/" + ticketId + "/reply");
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .header("Content-Type", "application/json")
                .post(RequestBody.create(bodyObj.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "replyToTicket ticket=" + ticketId);
    }

    /**
     * Update the status of a Freshdesk ticket.
     * Status codes: 2=Open, 3=Pending, 4=Resolved, 5=Closed.
     *
     * @param credential  channel credentials
     * @param ticketId    numeric ticket ID
     * @param statusCode  integer status code (e.g. 4 for Resolved, 5 for Closed)
     */
    public void updateTicketStatus(ChannelCredential credential, String ticketId, int statusCode) {
        JSONObject bodyObj = new JSONObject();
        bodyObj.put("status", statusCode);

        String url = buildUrl(credential, "/tickets/" + ticketId);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .header("Content-Type", "application/json")
                .put(RequestBody.create(bodyObj.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "updateTicketStatus ticket=" + ticketId + " status=" + statusCode);
    }

    /**
     * Verify credentials by calling {@code GET /api/v2/agents/me}.
     *
     * @return true if HTTP 200, false on any error or 4xx/5xx
     */
    public boolean ping(ChannelCredential credential) {
        String url = buildUrl(credential, "/agents/me");
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .get()
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                return response.isSuccessful();
            } finally {
                response.close();
            }
        } catch (IOException e) {
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build the Basic auth header using Freshdesk convention: {@code api_key:X}.
     */
    String buildAuthHeader(ChannelCredential credential) {
        Map<String, String> attrs = credential.attributes();
        String apiKey = attrs != null ? attrs.get("api_key") : null;
        if (apiKey == null || apiKey.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Freshdesk credential missing 'api_key' attribute");
        }
        return Credentials.basic(apiKey, "X");
    }

    String buildUrl(ChannelCredential credential, String path) {
        if (baseUrlOverride != null) {
            return baseUrlOverride + path;
        }
        Map<String, String> attrs = credential.attributes();
        String domain = attrs != null ? attrs.get("domain") : null;
        if (domain == null || domain.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Freshdesk credential missing 'domain' attribute");
        }
        // Accept both "acme" and "acme.freshdesk.com" — normalise to just subdomain
        String subdomain = domain.endsWith(".freshdesk.com")
                ? domain.replace(".freshdesk.com", "")
                : domain;
        return "https://" + subdomain + ".freshdesk.com/api/v2" + path;
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
                            "freshdesk",
                            "HTTP " + response.code() + " for " + context + ": " + body);
                }
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "freshdesk", "Network error for " + context, e);
        }
    }
}
