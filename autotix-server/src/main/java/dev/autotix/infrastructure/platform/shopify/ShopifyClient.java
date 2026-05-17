package dev.autotix.infrastructure.platform.shopify;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelCredential;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the Shopify Admin REST API (2024-01).
 *
 * <p>Authentication: {@code X-Shopify-Access-Token} header using the admin API token
 * stored in {@code credential.attributes().get("admin_api_token")}.
 *
 * <p>Base URL is derived from {@code credential.attributes().get("shop_domain")} as
 * {@code https://{shop_domain}/admin/api/2024-01}.
 *
 * <p>The package-private constructor accepts a pre-built {@link OkHttpClient} and a
 * base-URL override so tests can target a MockWebServer.
 */
@Component
public class ShopifyClient {

    private static final Logger log = LoggerFactory.getLogger(ShopifyClient.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final String API_VERSION = "2024-01";

    final OkHttpClient httpClient;
    private final String baseUrlOverride; // null in production; set in tests

    /** Production constructor. */
    public ShopifyClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        this.baseUrlOverride = null;
    }

    /** Package-private constructor for tests. */
    ShopifyClient(OkHttpClient httpClient, String baseUrlOverride) {
        this.httpClient = httpClient;
        this.baseUrlOverride = baseUrlOverride;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Fetch an order by its ID.
     *
     * @param credential must have {@code admin_api_token} and {@code shop_domain} attributes
     * @param orderId    Shopify order ID (numeric string)
     * @return parsed JSONObject for the order, or {@code null} if not found
     */
    public JSONObject getOrder(ChannelCredential credential, String orderId) {
        String url = buildUrl(credential, "/orders/" + orderId + ".json");
        Request request = new Request.Builder()
                .url(url)
                .header("X-Shopify-Access-Token", getToken(credential))
                .get()
                .build();
        String body = executeAndReturn(request, "getOrder orderId=" + orderId);
        if (body == null) return null;
        JSONObject root = JSON.parseObject(body);
        return root != null ? root.getJSONObject("order") : null;
    }

    /**
     * Fetch a customer by their ID.
     *
     * @param credential must have {@code admin_api_token} and {@code shop_domain} attributes
     * @param customerId Shopify customer ID (numeric string)
     * @return parsed JSONObject for the customer, or {@code null} if not found
     */
    public JSONObject getCustomer(ChannelCredential credential, String customerId) {
        String url = buildUrl(credential, "/customers/" + customerId + ".json");
        Request request = new Request.Builder()
                .url(url)
                .header("X-Shopify-Access-Token", getToken(credential))
                .get()
                .build();
        String body = executeAndReturn(request, "getCustomer customerId=" + customerId);
        if (body == null) return null;
        JSONObject root = JSON.parseObject(body);
        return root != null ? root.getJSONObject("customer") : null;
    }

    /**
     * Append a note to an existing Shopify order.
     *
     * <p>Shopify has no thread/comment API for orders. This method fetches the current
     * {@code note} field and appends a timestamped block:
     * <pre>
     * ---
     * 2024-06-01T10:00:00Z Autotix reply:
     * &lt;noteText&gt;
     * </pre>
     *
     * @param credential must have {@code admin_api_token} and {@code shop_domain} attributes
     * @param orderId    Shopify order ID (numeric string)
     * @param noteText   the reply text to append
     */
    public void appendOrderNote(ChannelCredential credential, String orderId, String noteText) {
        // Fetch current note
        JSONObject order = getOrder(credential, orderId);
        String existingNote = (order != null && order.getString("note") != null)
                ? order.getString("note")
                : "";

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(
                ZonedDateTime.now(ZoneOffset.UTC).toInstant());
        String appended = (existingNote.isEmpty() ? "" : existingNote + "\n")
                + "---\n" + timestamp + " Autotix reply:\n" + noteText;

        JSONObject orderBody = new JSONObject();
        orderBody.put("id", Long.parseLong(orderId));
        orderBody.put("note", appended);

        JSONObject root = new JSONObject();
        root.put("order", orderBody);

        String url = buildUrl(credential, "/orders/" + orderId + ".json");
        Request request = new Request.Builder()
                .url(url)
                .header("X-Shopify-Access-Token", getToken(credential))
                .header("Content-Type", "application/json")
                .put(RequestBody.create(root.toJSONString(), JSON_MEDIA_TYPE))
                .build();

        execute(request, "appendOrderNote orderId=" + orderId);
    }

    /**
     * Verify that the credentials are valid by calling {@code GET /shop.json}.
     *
     * @return {@code true} if the call returns HTTP 200, {@code false} on 4xx/5xx
     */
    public boolean ping(ChannelCredential credential) {
        String url = buildUrl(credential, "/shop.json");
        Request request = new Request.Builder()
                .url(url)
                .header("X-Shopify-Access-Token", getToken(credential))
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

    private String getToken(ChannelCredential credential) {
        Map<String, String> attrs = credential.attributes();
        String token = attrs != null ? attrs.get("admin_api_token") : null;
        if (token == null || token.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Shopify credential missing 'admin_api_token' attribute");
        }
        return token;
    }

    private String buildUrl(ChannelCredential credential, String path) {
        if (baseUrlOverride != null) {
            return baseUrlOverride + path;
        }
        Map<String, String> attrs = credential.attributes();
        String shopDomain = attrs != null ? attrs.get("shop_domain") : null;
        if (shopDomain == null || shopDomain.isEmpty()) {
            throw new AutotixException.ValidationException(
                    "Shopify credential missing 'shop_domain' attribute");
        }
        return "https://" + shopDomain + "/admin/api/" + API_VERSION + path;
    }

    private void execute(Request request, String context) {
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    throw new AutotixException.IntegrationException(
                            "shopify",
                            "HTTP " + response.code() + " for " + context);
                }
            } finally {
                response.close();
            }
        } catch (AutotixException e) {
            throw e;
        } catch (IOException e) {
            throw new AutotixException.IntegrationException(
                    "shopify", "Network error for " + context, e);
        }
    }

    /** Execute and return the response body as a string; returns null on non-2xx (instead of throwing). */
    private String executeAndReturn(Request request, String context) {
        try {
            Response response = httpClient.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    log.warn("[SHOPIFY] HTTP {} for {}", response.code(), context);
                    return null;
                }
                return response.body() != null ? response.body().string() : null;
            } finally {
                response.close();
            }
        } catch (IOException e) {
            log.warn("[SHOPIFY] Network error for {}: {}", context, e.getMessage());
            return null;
        }
    }
}
