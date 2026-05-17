package dev.autotix.infrastructure.notification;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.notification.NotificationChannel;
import dev.autotix.domain.notification.NotificationEventKind;
import dev.autotix.domain.notification.NotificationRoute;
import dev.autotix.domain.notification.NotificationRouteRepository;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dispatches outbound notifications to configured routes when a system event fires.
 *
 * Exceptions per-route are caught and logged — dispatch never propagates to the caller.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private final NotificationRouteRepository routeRepository;
    private final SystemEmailSender systemEmailSender;
    private final OkHttpClient httpClient;

    public NotificationDispatcher(NotificationRouteRepository routeRepository,
                                  SystemEmailSender systemEmailSender,
                                  OkHttpClient httpClient) {
        this.routeRepository = routeRepository;
        this.systemEmailSender = systemEmailSender;
        this.httpClient = httpClient;
    }

    /**
     * Find all enabled routes for the given event kind and fire them.
     *
     * @param kind    the event that occurred
     * @param context key→value substitution map for template placeholders, e.g. {ticketId: "42"}
     */
    public void dispatch(NotificationEventKind kind, Map<String, String> context) {
        List<NotificationRoute> routes;
        try {
            routes = routeRepository.findEnabledByEventKind(kind);
        } catch (Exception e) {
            log.error("[Notification] Failed to load routes for kind {}: {}", kind, e.getMessage(), e);
            return;
        }

        for (NotificationRoute route : routes) {
            try {
                fireRoute(route, context);
            } catch (Exception e) {
                log.error("[Notification] Route {} ({}) failed: {}", route.id(), route.name(), e.getMessage(), e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void fireRoute(NotificationRoute route, Map<String, String> context) {
        if (route.channel() == NotificationChannel.EMAIL) {
            sendEmail(route, context);
        } else if (route.channel() == NotificationChannel.SLACK_WEBHOOK) {
            sendSlack(route, context);
        } else {
            log.warn("[Notification] Unknown channel {} for route {}", route.channel(), route.id());
        }
    }

    private void sendEmail(NotificationRoute route, Map<String, String> context) {
        JSONObject cfg = parseConfig(route);
        if (cfg == null) return;

        JSONArray toArray = cfg.getJSONArray("to");
        List<String> recipients = new ArrayList<>();
        if (toArray != null) {
            for (int i = 0; i < toArray.size(); i++) {
                String addr = toArray.getString(i);
                if (addr != null && !addr.isEmpty()) {
                    recipients.add(addr);
                }
            }
        }
        if (recipients.isEmpty()) {
            log.warn("[Notification] Route {} EMAIL has no recipients in configJson", route.id());
            return;
        }

        String subjectTemplate = cfg.getString("subjectTemplate");
        if (subjectTemplate == null || subjectTemplate.isEmpty()) {
            subjectTemplate = "[Autotix] SLA breached on ticket {ticketId}";
        }
        String subject = renderTemplate(subjectTemplate, context);
        String body = buildEmailBody(context);

        systemEmailSender.send(recipients, subject, body);
        log.info("[Notification] EMAIL route {} fired for event", route.id());
    }

    private void sendSlack(NotificationRoute route, Map<String, String> context) {
        JSONObject cfg = parseConfig(route);
        if (cfg == null) return;

        String webhookUrl = cfg.getString("webhookUrl");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("[Notification] Route {} SLACK_WEBHOOK has no webhookUrl in configJson", route.id());
            return;
        }

        String messageTemplate = cfg.getString("messageTemplate");
        if (messageTemplate == null || messageTemplate.isEmpty()) {
            messageTemplate = ":warning: SLA breached on ticket {ticketId}: {subject}";
        }
        String message = renderTemplate(messageTemplate, context);

        JSONObject payload = new JSONObject();
        payload.put("text", message);
        String jsonBody = payload.toJSONString();

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("[Notification] Slack webhook route {} returned HTTP {}: {}",
                        route.id(), response.code(), response.message());
            } else {
                log.info("[Notification] SLACK_WEBHOOK route {} fired successfully", route.id());
            }
        } catch (IOException e) {
            log.error("[Notification] Slack webhook route {} IO error: {}", route.id(), e.getMessage(), e);
        }
    }

    /**
     * Replace {key} placeholders in a template string with values from the context map.
     */
    static String renderTemplate(String template, Map<String, String> context) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    private static String buildEmailBody(Map<String, String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("SLA Breach Notification\n");
        sb.append("=======================\n\n");
        appendIfPresent(sb, "Ticket ID", context.get("ticketId"));
        appendIfPresent(sb, "External Ticket ID", context.get("externalTicketId"));
        appendIfPresent(sb, "Subject", context.get("subject"));
        appendIfPresent(sb, "Customer", context.get("customerIdentifier"));
        appendIfPresent(sb, "Priority", context.get("priority"));
        appendIfPresent(sb, "Status", context.get("status"));
        appendIfPresent(sb, "Breached At", context.get("breachedAt"));
        appendIfPresent(sb, "Ticket URL", context.get("ticketUrl"));
        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private JSONObject parseConfig(NotificationRoute route) {
        try {
            return JSON.parseObject(route.configJson());
        } catch (Exception e) {
            log.error("[Notification] Route {} has invalid configJson: {}", route.id(), e.getMessage());
            return null;
        }
    }
}
