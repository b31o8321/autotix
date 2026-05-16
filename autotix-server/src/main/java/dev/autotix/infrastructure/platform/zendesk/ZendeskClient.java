package dev.autotix.infrastructure.platform.zendesk;

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
 * HTTP client for the Zendesk REST API.
 *
 * <p>Authentication: Bearer token from {@code credential.accessToken()}.
 *
 * <p>Subdomain: read from {@code credential.attributes().get("subdomain")}.
 * All API calls are directed to {@code https://{subdomain}.zendesk.com}.
 *
 * <p>The test-friendly package-private constructor accepts a pre-built {@link OkHttpClient}
 * and a base-URL override so tests can target a {@code MockWebServer}.
 */
@Component
public class ZendeskClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    final OkHttpClient httpClient;
    private final String baseUrlOverride; // null in production; set in tests

    /** Production constructor. */
    public ZendeskClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrlOverride = null;
    }

    /** Package-private constructor for tests. */
    ZendeskClient(OkHttpClient httpClient, String baseUrlOverride) {
        this.httpClient = httpClient;
        this.baseUrlOverride = baseUrlOverride;
    }

    /**
     * Post a public HTML comment to a Zendesk ticket.
     *
     * @param credential  must have {@code accessToken} and {@code attributes["subdomain"]}
     * @param ticketId    the Zendesk ticket numeric id (as string)
     * @param htmlBody    the HTML body to post as a public comment
     */
    public void postComment(ChannelCredential credential, String ticketId, String htmlBody) {
        // Build body: {ticket: {comment: {html_body, public: true}}}
        JSONObject comment = new JSONObject();
        comment.put("html_body", htmlBody);
        comment.put("public", true);

        JSONObject ticketObj = new JSONObject();
        ticketObj.put("comment", comment);

        JSONObject body = new JSONObject();
        body.put("ticket", ticketObj);

        String url = buildUrl(credential, "/api/v2/tickets/" + ticketId + ".json");
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .header("Content-Type", "application/json")
                .put(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "postComment ticket=" + ticketId);
    }

    /**
     * Update the status of a Zendesk ticket.
     *
     * @param credential must have {@code accessToken} and {@code attributes["subdomain"]}
     * @param ticketId   the Zendesk ticket numeric id (as string)
     * @param status     e.g. "solved", "open", "pending"
     */
    public void updateStatus(ChannelCredential credential, String ticketId, String status) {
        JSONObject ticketObj = new JSONObject();
        ticketObj.put("status", status);

        JSONObject body = new JSONObject();
        body.put("ticket", ticketObj);

        String url = buildUrl(credential, "/api/v2/tickets/" + ticketId + ".json");
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .header("Content-Type", "application/json")
                .put(RequestBody.create(body.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "updateStatus ticket=" + ticketId + " status=" + status);
    }

    /**
     * Verify credentials are valid by calling {@code GET /api/v2/users/me.json}.
     *
     * @return true if the call returns HTTP 200, false on 4xx/5xx
     */
    public boolean ping(ChannelCredential credential) {
        String url = buildUrl(credential, "/api/v2/users/me.json");
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

    /**
     * OAuth code exchange.
     *
     * <p>NOT implemented in v1 — the per-tenant subdomain + client_secret are not in scope yet.
     * This method will be implemented in v2 when the OAuth flow is fully designed.
     *
     * @throws UnsupportedOperationException always
     */
    public ChannelCredential oauthExchange(String code) {
        throw new UnsupportedOperationException("OAuth flow implemented in v2");
    }

    /**
     * Download an arbitrary URL (e.g. Zendesk attachment content_url) using the credential's auth header.
     *
     * @return {@link AttachmentDownload} with raw bytes and content-type, or {@code null} on non-2xx
     */
    public AttachmentDownload fetchAttachment(ChannelCredential credential, String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", buildAuthHeader(credential))
                .get()
                .build();
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    return null;
                }
                byte[] bytes = response.body() != null ? response.body().bytes() : new byte[0];
                String contentType = response.header("Content-Type", "application/octet-stream");
                return new AttachmentDownload(bytes, contentType);
            } finally {
                response.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Result of a {@link #fetchAttachment} call.
     */
    public static final class AttachmentDownload {
        public final byte[] bytes;
        public final String contentType;

        public AttachmentDownload(byte[] bytes, String contentType) {
            this.bytes = bytes;
            this.contentType = contentType;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds the Authorization header value.
     * Prefers HTTP Basic ({email}/token:{api_token}) when api_token is present in attributes;
     * falls back to Bearer {accessToken} for back-compat with credentials created before this change.
     */
    String buildAuthHeader(ChannelCredential credential) {
        Map<String, String> attrs = credential.attributes();
        if (attrs != null) {
            String apiToken = attrs.get("api_token");
            String email = attrs.get("email");
            if (apiToken != null && !apiToken.isEmpty() && email != null && !email.isEmpty()) {
                // Zendesk API token basic auth: username = "{email}/token", password = "{api_token}"
                return Credentials.basic(email + "/token", apiToken);
            }
        }
        // Legacy / OAuth path
        return "Bearer " + credential.accessToken();
    }

    private String buildUrl(ChannelCredential credential, String path) {
        if (baseUrlOverride != null) {
            return baseUrlOverride + path;
        }
        String subdomain = credential.attributes() != null
                ? credential.attributes().get("subdomain")
                : null;
        if (subdomain == null || subdomain.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Zendesk credential missing 'subdomain' attribute");
        }
        return "https://" + subdomain + ".zendesk.com" + path;
    }

    private void execute(Request request, String context) {
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    throw new AutotixException.IntegrationException(
                            "zendesk",
                            "HTTP " + response.code() + " for " + context);
                }
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "zendesk", "Network error for " + context, e);
        }
    }
}
