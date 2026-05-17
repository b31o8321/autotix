package dev.autotix.infrastructure.platform.line;

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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LineWebhookParser unit tests.
 */
class LineWebhookParserTest {

    private static final String CHANNEL_SECRET = "test-secret";

    private LineWebhookParser parser;
    private Channel channel;

    @BeforeEach
    void setUp() {
        parser = new LineWebhookParser();

        Map<String, String> attrs = new HashMap<>();
        attrs.put("channel_access_token", "test-access-token");
        attrs.put("channel_secret", CHANNEL_SECRET);
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);

        channel = Channel.rehydrate(
                new ChannelId("ch-line-1"),
                PlatformType.LINE,
                ChannelType.CHAT,
                "Test LINE",
                "webhookToken999",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // Valid HMAC + 1 text event → 1 parsed event
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_validHmac_oneTextEvent_returnsSingleEvent() throws Exception {
        String body = singleTextEventPayload("Ufoo123", "Hello Autotix", 1700000000000L);
        Map<String, String> headers = signedHeaders(body);

        List<TicketEvent> events = parser.parseAndVerify(channel, headers, body);

        assertEquals(1, events.size());
        TicketEvent e = events.get(0);
        assertEquals(EventType.NEW_TICKET, e.type());
        assertEquals("line:Ufoo123", e.customerIdentifier());
        assertEquals("Ufoo123", e.externalTicketId());
        assertEquals("Hello Autotix", e.messageBody());
        assertEquals("Hello Autotix", e.subject());
        assertEquals(channel.id(), e.channelId());
    }

    // -----------------------------------------------------------------------
    // 3 text events in one POST → 3 parsed events
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_threeTextEvents_returnsThree() throws Exception {
        String body = "{\"events\":[" +
                textEventJson("Ua1", "Msg 1", 1L) + "," +
                textEventJson("Ua2", "Msg 2", 2L) + "," +
                textEventJson("Ua3", "Msg 3", 3L) +
                "]}";
        Map<String, String> headers = signedHeaders(body);

        List<TicketEvent> events = parser.parseAndVerify(channel, headers, body);

        assertEquals(3, events.size());
        assertEquals("line:Ua1", events.get(0).customerIdentifier());
        assertEquals("line:Ua2", events.get(1).customerIdentifier());
        assertEquals("line:Ua3", events.get(2).customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Mixed text + sticker → only text returned
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_mixedTextAndSticker_returnsOnlyText() throws Exception {
        String stickerEvent = "{\"type\":\"message\",\"message\":{\"type\":\"sticker\",\"stickerId\":\"1\",\"packageId\":\"1\"}," +
                "\"source\":{\"userId\":\"Usticker\"},\"replyToken\":\"token\",\"timestamp\":100}";
        String body = "{\"events\":[" +
                textEventJson("Utext", "Hi there", 200L) + "," +
                stickerEvent +
                "]}";
        Map<String, String> headers = signedHeaders(body);

        List<TicketEvent> events = parser.parseAndVerify(channel, headers, body);

        assertEquals(1, events.size());
        assertEquals("line:Utext", events.get(0).customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Invalid HMAC → throws AuthException
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_invalidHmac_throwsAuthException() {
        String body = singleTextEventPayload("Ufoo", "hi", 1L);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-line-signature", "aW52YWxpZA=="); // invalid base64 sig

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channel, headers, body));
    }

    // -----------------------------------------------------------------------
    // Missing signature header → throws AuthException
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_missingSignatureHeader_throwsAuthException() {
        String body = singleTextEventPayload("Ufoo", "hi", 1L);
        Map<String, String> headers = Collections.emptyMap();

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channel, headers, body));
    }

    // -----------------------------------------------------------------------
    // Empty events array → empty result
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_emptyEventsArray_returnsEmpty() throws Exception {
        String body = "{\"events\":[]}";
        Map<String, String> headers = signedHeaders(body);

        List<TicketEvent> events = parser.parseAndVerify(channel, headers, body);

        assertTrue(events.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Subject truncation at 60 chars
    // -----------------------------------------------------------------------

    @Test
    void parse_longText_subjectTruncatedAt60Chars() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) sb.append("ABCDEFGHIJ");
        String longText = sb.toString(); // 70 chars
        String body = singleTextEventPayload("Ulong", longText, 1L);

        List<TicketEvent> events = parser.parse(channel, body);

        assertEquals(1, events.size());
        assertEquals(60, events.get(0).subject().length());
        assertEquals(longText, events.get(0).messageBody()); // body is full text
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private String singleTextEventPayload(String userId, String text, long timestampMs) {
        return "{\"events\":[" + textEventJson(userId, text, timestampMs) + "]}";
    }

    private String textEventJson(String userId, String text, long timestampMs) {
        return "{\"type\":\"message\"," +
                "\"message\":{\"type\":\"text\",\"text\":\"" + text + "\"}," +
                "\"source\":{\"userId\":\"" + userId + "\"}," +
                "\"replyToken\":\"replytoken\"," +
                "\"timestamp\":" + timestampMs + "}";
    }

    private Map<String, String> signedHeaders(String body) throws Exception {
        String sig = computeHmac(CHANNEL_SECRET, body);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-line-signature", sig);
        return headers;
    }

    private String computeHmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }
}
