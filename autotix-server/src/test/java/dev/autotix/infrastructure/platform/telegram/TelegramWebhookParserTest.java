package dev.autotix.infrastructure.platform.telegram;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TelegramWebhookParser unit tests.
 */
class TelegramWebhookParserTest {

    private TelegramWebhookParser parser;
    private Channel channelNoSecret;
    private Channel channelWithSecret;

    private static final String SECRET = "myTelegramSecret";

    @BeforeEach
    void setUp() {
        parser = new TelegramWebhookParser();

        Map<String, String> attrsNoSecret = new HashMap<>();
        attrsNoSecret.put("bot_token", "111:NoSecretToken");
        ChannelCredential credNoSecret = new ChannelCredential(null, null, null, attrsNoSecret);
        channelNoSecret = Channel.rehydrate(
                new ChannelId("ch-tg-1"),
                PlatformType.TELEGRAM,
                ChannelType.CHAT,
                "Test Telegram",
                "webhookToken111",
                credNoSecret,
                true,
                true,
                Instant.now(),
                Instant.now());

        Map<String, String> attrsWithSecret = new HashMap<>(attrsNoSecret);
        attrsWithSecret.put("secret_token", SECRET);
        ChannelCredential credWithSecret = new ChannelCredential(null, null, null, attrsWithSecret);
        channelWithSecret = Channel.rehydrate(
                new ChannelId("ch-tg-2"),
                PlatformType.TELEGRAM,
                ChannelType.CHAT,
                "Test Telegram Secret",
                "webhookToken222",
                credWithSecret,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // Valid text message → parsed event with telegram: prefix
    // -----------------------------------------------------------------------

    @Test
    void parse_validTextMessage_producesNewTicketEvent() {
        String body = "{"
                + "\"update_id\": 100,"
                + "\"message\": {"
                + "  \"message_id\": 1,"
                + "  \"from\": {\"id\": 987654, \"username\": \"johndoe\", \"first_name\": \"John\"},"
                + "  \"chat\": {\"id\": 987654, \"type\": \"private\"},"
                + "  \"text\": \"Hello, I need help!\","
                + "  \"date\": 1700000000"
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("987654", event.externalTicketId());
        assertEquals("telegram:johndoe", event.customerIdentifier());
        assertEquals("johndoe", event.customerName());
        assertEquals("Hello, I need help!", event.messageBody());
        assertEquals("Hello, I need help!", event.subject());
    }

    @Test
    void parse_textMessage_subjectTruncatedAt60Chars() {
        StringBuilder _sb = new StringBuilder();
        for (int i = 0; i < 10; i++) _sb.append("ABCDEFGHIJ");
        String longText = _sb.toString(); // 100 chars
        String body = "{"
                + "\"update_id\": 101,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 1, \"username\": \"u\"},"
                + "  \"chat\": {\"id\": 1},"
                + "  \"text\": \"" + longText + "\""
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(60, event.subject().length(), "Subject should be truncated to 60 chars");
        assertEquals(longText, event.messageBody(), "Full body should be preserved");
    }

    @Test
    void parse_usernameFallsBackToFirstName() {
        String body = "{"
                + "\"update_id\": 102,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 555, \"first_name\": \"Alice\"},"
                + "  \"chat\": {\"id\": 555},"
                + "  \"text\": \"Hi\""
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals("telegram:Alice", event.customerIdentifier());
        assertEquals("Alice", event.customerName());
    }

    // -----------------------------------------------------------------------
    // Non-text payload (sticker) → IGNORED
    // -----------------------------------------------------------------------

    @Test
    void parse_stickerMessage_producesIgnoredEvent() {
        String body = "{"
                + "\"update_id\": 200,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 123},"
                + "  \"chat\": {\"id\": 123},"
                + "  \"sticker\": {\"file_id\": \"abc\", \"emoji\": \"😊\"}"
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.IGNORED, event.type());
    }

    @Test
    void parse_noMessageField_producesIgnoredEvent() {
        // e.g. callback_query update
        String body = "{"
                + "\"update_id\": 201,"
                + "\"callback_query\": {\"id\": \"abc\", \"data\": \"button_click\"}"
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // Secret verification
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_secretConfigured_matchingHeader_passes() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Telegram-Bot-Api-Secret-Token", SECRET);

        String body = "{"
                + "\"update_id\": 300,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 1, \"username\": \"bob\"},"
                + "  \"chat\": {\"id\": 1},"
                + "  \"text\": \"Hello\""
                + "}}";

        TicketEvent event = parser.parseAndVerify(channelWithSecret, headers, body);

        assertNotNull(event);
        assertEquals(EventType.NEW_TICKET, event.type());
    }

    @Test
    void parseAndVerify_secretConfigured_missingHeader_throwsAuthException() {
        Map<String, String> headers = Collections.emptyMap();
        String body = "{\"update_id\": 301, \"message\": {\"from\":{\"id\":1},\"chat\":{\"id\":1},\"text\":\"hi\"}}";

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channelWithSecret, headers, body),
                "Missing secret header should throw AuthException");
    }

    @Test
    void parseAndVerify_secretConfigured_mismatchingHeader_throwsAuthException() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Telegram-Bot-Api-Secret-Token", "wrongSecret");

        String body = "{\"update_id\": 302, \"message\": {\"from\":{\"id\":1},\"chat\":{\"id\":1},\"text\":\"hi\"}}";

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channelWithSecret, headers, body),
                "Wrong secret should throw AuthException");
    }

    @Test
    void parseAndVerify_secretUnset_allowsWithoutHeader() {
        Map<String, String> headers = Collections.emptyMap();
        String body = "{"
                + "\"update_id\": 303,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 2, \"username\": \"carol\"},"
                + "  \"chat\": {\"id\": 2},"
                + "  \"text\": \"Test message\""
                + "}}";

        // Should not throw even without the secret header
        assertDoesNotThrow(() -> parser.parseAndVerify(channelNoSecret, headers, body));
    }

    @Test
    void parseAndVerify_caseInsensitiveHeaderLookup_passes() {
        Map<String, String> headers = new HashMap<>();
        // lowercase variant
        headers.put("x-telegram-bot-api-secret-token", SECRET);

        String body = "{"
                + "\"update_id\": 304,"
                + "\"message\": {"
                + "  \"from\": {\"id\": 3, \"username\": \"dave\"},"
                + "  \"chat\": {\"id\": 3},"
                + "  \"text\": \"Case test\""
                + "}}";

        assertDoesNotThrow(() -> parser.parseAndVerify(channelWithSecret, headers, body));
    }
}
