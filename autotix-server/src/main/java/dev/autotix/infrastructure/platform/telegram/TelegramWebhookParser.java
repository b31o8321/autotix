package dev.autotix.infrastructure.platform.telegram;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Telegram Bot API Update payloads into {@link TicketEvent}s.
 *
 * <h3>Verification</h3>
 * If the channel credential has a {@code secret_token} attribute, the
 * {@code X-Telegram-Bot-Api-Secret-Token} header is checked via constant-time comparison.
 * Missing or mismatching header when secret is configured → {@link AutotixException.AuthException}.
 * Secret not configured → allow with WARN log.
 *
 * <h3>v1 scope</h3>
 * Only handles {@code message.text}. Updates without a text message return an empty event
 * (type={@link EventType#IGNORED}).
 */
@Component
public class TelegramWebhookParser {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookParser.class);

    /** Telegram sends this header when a secret_token was set during setWebhook. */
    private static final String SECRET_HEADER = "x-telegram-bot-api-secret-token";

    /** Max chars to use for auto-generated ticket subject. */
    private static final int SUBJECT_MAX_LEN = 60;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Verify the optional secret token and parse the Telegram Update.
     *
     * @param channel  the configured channel (credentials + id)
     * @param headers  HTTP request headers (case-insensitive lookup)
     * @param rawBody  raw JSON body from Telegram
     * @return a single {@link TicketEvent}, or an IGNORED event if no text message is present
     */
    public TicketEvent parseAndVerify(Channel channel, Map<String, String> headers, String rawBody) {
        String secret = extractSecret(channel);

        if (secret != null && !secret.isEmpty()) {
            String provided = findHeader(headers, SECRET_HEADER);
            if (provided == null || provided.isEmpty()) {
                throw new AutotixException.AuthException(
                        "Telegram webhook: X-Telegram-Bot-Api-Secret-Token header is missing " +
                        "but secret_token is configured for channel " + channel.id());
            }
            if (!constantTimeEquals(secret, provided)) {
                throw new AutotixException.AuthException(
                        "Telegram webhook: X-Telegram-Bot-Api-Secret-Token header mismatch " +
                        "for channel " + channel.id());
            }
        } else {
            log.warn("[TELEGRAM] No secret_token configured for channel {}; " +
                     "accepting webhook without token verification.", channel.id());
        }

        return parse(channel, rawBody);
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    TicketEvent parse(Channel channel, String rawBody) {
        JSONObject root = JSON.parseObject(rawBody);
        if (root == null) root = new JSONObject();

        JSONObject message = root.getJSONObject("message");
        if (message == null) {
            // Non-message update (callback_query, inline, etc.) — ignore
            log.debug("[TELEGRAM] Ignoring update without 'message' field for channel {}", channel.id());
            return ignoredEvent(channel, root);
        }

        String text = message.getString("text");
        if (text == null || text.isEmpty()) {
            // Sticker, photo, etc. — ignore
            log.debug("[TELEGRAM] Ignoring non-text message for channel {}", channel.id());
            return ignoredEvent(channel, root);
        }

        // Extract identity fields
        JSONObject from = message.getJSONObject("from");
        JSONObject chat = message.getJSONObject("chat");

        String chatId = chat != null ? String.valueOf(chat.getLongValue("id")) : "";
        String fromId = from != null ? String.valueOf(from.getLongValue("id")) : chatId;

        String username = null;
        if (from != null) {
            username = from.getString("username");
            if (username == null || username.isEmpty()) {
                username = from.getString("first_name");
            }
        }
        if (username == null || username.isEmpty()) {
            username = chatId;
        }

        String customerIdentifier = "telegram:" + username;
        String customerName = username;

        // Subject: first SUBJECT_MAX_LEN chars of text
        String subject = text.length() > SUBJECT_MAX_LEN
                ? text.substring(0, SUBJECT_MAX_LEN)
                : text;
        if (subject.isEmpty()) subject = "Telegram message";

        // externalTicketId = chatId (one chat = one thread)
        String externalTicketId = chatId;

        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("update_id", root.getLong("update_id"));
        raw.put("chat_id", chatId);
        raw.put("from_id", fromId);
        raw.put("body", rawBody);

        return new TicketEvent(
                channel.id(),
                EventType.NEW_TICKET,
                externalTicketId,
                customerIdentifier,
                customerName,
                subject,
                text,
                Instant.now(),
                raw,
                Collections.<TicketEvent.InboundAttachment>emptyList());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TicketEvent ignoredEvent(Channel channel, JSONObject root) {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("update_id", root != null ? root.getLong("update_id") : null);
        return new TicketEvent(
                channel.id(),
                EventType.IGNORED,
                "",
                "",
                "",
                null,
                "",
                Instant.now(),
                raw,
                Collections.<TicketEvent.InboundAttachment>emptyList());
    }

    private String extractSecret(Channel channel) {
        if (channel.credential() == null || channel.credential().attributes() == null) {
            return null;
        }
        return channel.credential().attributes().get("secret_token");
    }

    /** Case-insensitive header lookup. */
    private String findHeader(Map<String, String> headers, String name) {
        if (headers == null) return null;
        String val = headers.get(name);
        if (val != null) return val;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Constant-time string comparison to mitigate timing attacks. */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
