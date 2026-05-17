package dev.autotix.infrastructure.platform.wecom;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client for the WeCom (企业微信) Customer Service (微信客服) API.
 *
 * <h3>Access-token lifecycle</h3>
 * Tokens are cached in a per-instance {@link AtomicReference}. On expiry (with a 60-second
 * safety margin), the next call transparently re-fetches. All token operations are
 * synchronised on a dedicated lock to prevent thundering-herd refreshes.
 *
 * <h3>API base URL</h3>
 * Overridable in the package-private constructor (used in tests via MockWebServer).
 */
@Component
public class WecomClient {

    private static final Logger log = LoggerFactory.getLogger(WecomClient.class);
    static final String WECOM_API_BASE = "https://qyapi.weixin.qq.com";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;
    private static final long EXPIRE_MARGIN_MS = 60_000L; // refresh 60s before expiry

    final OkHttpClient httpClient;
    private final String baseUrl;

    // Token cache: keyed on corpid+secret to support multiple channels on same client
    // For simplicity, we cache only the last-used credentials (sufficient for single-channel use)
    private final Object tokenLock = new Object();
    private final AtomicReference<TokenBox> cachedToken = new AtomicReference<>();
    private String cachedCorpId = null;
    private String cachedSecret = null;

    /** Production constructor. */
    public WecomClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrl = WECOM_API_BASE;
    }

    /** Test constructor — accepts a pre-built client and a base-URL override. */
    WecomClient(OkHttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    // -------------------------------------------------------------------------
    // Token management
    // -------------------------------------------------------------------------

    /**
     * Returns a valid access token for the given credentials, refreshing on expiry.
     * Thread-safe; caches the token and avoids redundant HTTP calls within the TTL.
     */
    public String getAccessToken(String corpId, String secret) {
        synchronized (tokenLock) {
            TokenBox box = cachedToken.get();
            if (box != null && corpId.equals(cachedCorpId) && secret.equals(cachedSecret)
                    && box.expiresAt > System.currentTimeMillis()) {
                return box.token;
            }
            // Refresh
            String url = baseUrl + "/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + secret;
            Request request = new Request.Builder().url(url).get().build();
            try {
                Response resp = httpClient.newCall(request).execute();
                try {
                    String body = resp.body() != null ? resp.body().string() : "{}";
                    if (!resp.isSuccessful()) {
                        throw new AutotixException.AuthException(
                                "WeCom gettoken HTTP " + resp.code() + ": " + body);
                    }
                    JSONObject json = JSON.parseObject(body);
                    int errCode = json.getIntValue("errcode");
                    if (errCode != 0) {
                        throw new AutotixException.AuthException(
                                "WeCom gettoken errcode=" + errCode + " errmsg=" +
                                json.getString("errmsg"));
                    }
                    String token = json.getString("access_token");
                    int expiresIn = json.getIntValue("expires_in"); // seconds
                    long expiresAt = System.currentTimeMillis() + (expiresIn * 1000L) - EXPIRE_MARGIN_MS;
                    TokenBox newBox = new TokenBox(token, expiresAt);
                    cachedToken.set(newBox);
                    cachedCorpId = corpId;
                    cachedSecret = secret;
                    log.debug("[WeCom] Access token refreshed, expires in {}s", expiresIn);
                    return token;
                } finally {
                    resp.close();
                }
            } catch (AutotixException e) {
                throw e;
            } catch (IOException e) {
                throw new AutotixException.IntegrationException("wecom", "Network error fetching access token", e);
            }
        }
    }

    // -------------------------------------------------------------------------
    // kf/sync_msg
    // -------------------------------------------------------------------------

    /**
     * Pulls messages from WeCom's kf/sync_msg endpoint.
     *
     * @param accessToken valid WeCom access token
     * @param voucher     notification voucher from the webhook event
     * @param openKfId    Customer Service account ID
     * @param cursor      pagination cursor (null or empty for first call)
     * @param limit       max messages per page (recommended: 1000)
     * @return parsed JSON response containing {@code msg_list}, {@code next_cursor}, {@code has_more}
     */
    public JSONObject syncMsg(String accessToken, String voucher, String openKfId,
                              String cursor, int limit) {
        JSONObject reqBody = new JSONObject();
        reqBody.put("voucher", voucher);
        reqBody.put("open_kfid", openKfId);
        if (cursor != null && !cursor.isEmpty()) {
            reqBody.put("cursor", cursor);
        }
        reqBody.put("limit", limit);

        String url = baseUrl + "/cgi-bin/kf/sync_msg?access_token=" + accessToken;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(reqBody.toJSONString(), JSON_TYPE))
                .build();

        try {
            Response resp = httpClient.newCall(request).execute();
            try {
                String body = resp.body() != null ? resp.body().string() : "{}";
                if (!resp.isSuccessful()) {
                    throw new AutotixException.IntegrationException(
                            "wecom", "kf/sync_msg HTTP " + resp.code() + ": " + body);
                }
                JSONObject json = JSON.parseObject(body);
                int errCode = json.getIntValue("errcode");
                if (errCode != 0) {
                    throw new AutotixException.IntegrationException(
                            "wecom", "kf/sync_msg errcode=" + errCode + " errmsg=" +
                            json.getString("errmsg"));
                }
                return json;
            } finally {
                resp.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException("wecom", "Network error calling kf/sync_msg", e);
        }
    }

    // -------------------------------------------------------------------------
    // kf/send_msg
    // -------------------------------------------------------------------------

    /**
     * Sends a plain-text reply to a WeCom customer.
     *
     * @param accessToken valid WeCom access token
     * @param toUser      external_userid of the customer
     * @param openKfId    Customer Service account ID
     * @param content     text content to send
     */
    public void sendText(String accessToken, String toUser, String openKfId, String content) {
        JSONObject textNode = new JSONObject();
        textNode.put("content", content);

        JSONObject reqBody = new JSONObject();
        reqBody.put("touser", toUser);
        reqBody.put("open_kfid", openKfId);
        reqBody.put("msgtype", "text");
        reqBody.put("text", textNode);

        String url = baseUrl + "/cgi-bin/kf/send_msg?access_token=" + accessToken;
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(reqBody.toJSONString(), JSON_TYPE))
                .build();

        try {
            Response resp = httpClient.newCall(request).execute();
            try {
                String body = resp.body() != null ? resp.body().string() : "{}";
                if (!resp.isSuccessful()) {
                    throw new AutotixException.IntegrationException(
                            "wecom", "kf/send_msg HTTP " + resp.code() + ": " + body);
                }
                JSONObject json = JSON.parseObject(body);
                int errCode = json.getIntValue("errcode");
                if (errCode != 0) {
                    throw new AutotixException.IntegrationException(
                            "wecom", "kf/send_msg errcode=" + errCode + " errmsg=" +
                            json.getString("errmsg"));
                }
                log.debug("[WeCom] Text sent to user={} kfid={}", toUser, openKfId);
            } finally {
                resp.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException("wecom", "Network error calling kf/send_msg", e);
        }
    }

    // -------------------------------------------------------------------------
    // Health check
    // -------------------------------------------------------------------------

    /**
     * Verifies credentials by attempting to obtain an access token.
     *
     * @return {@code true} if token fetch succeeds
     * @throws AutotixException.AuthException on invalid credentials
     */
    public boolean ping(String corpId, String secret) {
        getAccessToken(corpId, secret); // throws on failure
        return true;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    static final class TokenBox {
        final String token;
        final long expiresAt; // System.currentTimeMillis() when this expires

        TokenBox(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
