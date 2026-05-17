package dev.autotix.infrastructure.platform.freshdesk;

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
 * FreshdeskWebhookParser unit tests.
 */
class FreshdeskWebhookParserTest {

    private FreshdeskWebhookParser parser;
    private Channel channelNoSecret;
    private Channel channelWithSecret;

    private static final String SECRET = "myWebhookSecret";

    @BeforeEach
    void setUp() {
        parser = new FreshdeskWebhookParser();

        Map<String, String> attrsNoSecret = new HashMap<>();
        attrsNoSecret.put("domain", "acme");
        attrsNoSecret.put("api_key", "k");
        ChannelCredential credNoSecret = new ChannelCredential(null, null, null, attrsNoSecret);
        channelNoSecret = Channel.rehydrate(
                new ChannelId("ch-fd-1"),
                PlatformType.FRESHDESK,
                ChannelType.EMAIL,
                "Test Freshdesk",
                "webhookToken111",
                credNoSecret,
                true,
                true,
                Instant.now(),
                Instant.now());

        Map<String, String> attrsWithSecret = new HashMap<>(attrsNoSecret);
        attrsWithSecret.put("webhook_secret", SECRET);
        ChannelCredential credWithSecret = new ChannelCredential(null, null, null, attrsWithSecret);
        channelWithSecret = Channel.rehydrate(
                new ChannelId("ch-fd-2"),
                PlatformType.FRESHDESK,
                ChannelType.EMAIL,
                "Test Freshdesk Secret",
                "webhookToken222",
                credWithSecret,
                true,
                true,
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // Parsing: ticket_created nested under freshdesk_webhook
    // -----------------------------------------------------------------------

    @Test
    void parse_ticketCreated_nestedShape_producesNewTicket() {
        String body = "{"
                + "\"freshdesk_webhook\": {"
                + "  \"ticket_id\": 12345,"
                + "  \"ticket_subject\": \"Help with login\","
                + "  \"ticket_description\": \"I cannot log in.\","
                + "  \"ticket_requester_email\": \"jane@example.com\","
                + "  \"ticket_requester_name\": \"Jane Doe\","
                + "  \"triggered_event\": \"ticket_created\","
                + "  \"ticket_status\": \"Open\""
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("12345", event.externalTicketId());
        assertEquals("Help with login", event.subject());
        assertEquals("I cannot log in.", event.messageBody());
        assertEquals("jane@example.com", event.customerIdentifier());
        assertEquals("Jane Doe", event.customerName());
    }

    // -----------------------------------------------------------------------
    // Parsing: ticket_created flat payload shape
    // -----------------------------------------------------------------------

    @Test
    void parse_ticketCreated_flatShape_producesNewTicket() {
        String body = "{"
                + "\"ticket_id\": 99,"
                + "\"ticket_subject\": \"Billing issue\","
                + "\"ticket_description\": \"I was charged twice.\","
                + "\"ticket_requester_email\": \"bob@test.com\","
                + "\"ticket_requester_name\": \"Bob\","
                + "\"triggered_event\": \"ticket_created\","
                + "\"ticket_status\": \"Open\""
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.NEW_TICKET, event.type());
        assertEquals("99", event.externalTicketId());
        assertEquals("Billing issue", event.subject());
        assertEquals("bob@test.com", event.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Parsing: note_added → NEW_MESSAGE
    // -----------------------------------------------------------------------

    @Test
    void parse_noteAdded_producesNewMessage() {
        String body = "{"
                + "\"freshdesk_webhook\": {"
                + "  \"ticket_id\": 555,"
                + "  \"ticket_subject\": \"Existing ticket\","
                + "  \"ticket_description\": \"New note from customer.\","
                + "  \"ticket_requester_email\": \"alice@example.com\","
                + "  \"ticket_requester_name\": \"Alice\","
                + "  \"triggered_event\": \"note_added\","
                + "  \"ticket_status\": \"Open\""
                + "}}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.NEW_MESSAGE, event.type());
        assertEquals("555", event.externalTicketId());
        assertEquals("alice@example.com", event.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // Parsing: status_changed to Resolved → STATUS_CHANGE
    // -----------------------------------------------------------------------

    @Test
    void parse_statusChangedToResolved_producesStatusChange() {
        String body = "{"
                + "\"ticket_id\": 77,"
                + "\"ticket_subject\": \"Issue\","
                + "\"ticket_description\": \"\","
                + "\"ticket_requester_email\": \"c@test.com\","
                + "\"ticket_requester_name\": \"C\","
                + "\"triggered_event\": \"status_changed\","
                + "\"ticket_status\": \"Resolved\""
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.STATUS_CHANGE, event.type());
    }

    // -----------------------------------------------------------------------
    // Parsing: status_changed to Closed → STATUS_CHANGE
    // -----------------------------------------------------------------------

    @Test
    void parse_statusChangedToClosed_producesStatusChange() {
        String body = "{"
                + "\"ticket_id\": 78,"
                + "\"ticket_subject\": \"Issue\","
                + "\"ticket_description\": \"\","
                + "\"ticket_requester_email\": \"d@test.com\","
                + "\"ticket_requester_name\": \"D\","
                + "\"triggered_event\": \"status_changed\","
                + "\"ticket_status\": \"Closed\""
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.STATUS_CHANGE, event.type());
    }

    // -----------------------------------------------------------------------
    // Parsing: status_changed to Open → IGNORED
    // -----------------------------------------------------------------------

    @Test
    void parse_statusChangedToOpen_producesIgnored() {
        String body = "{"
                + "\"ticket_id\": 80,"
                + "\"ticket_subject\": \"Issue\","
                + "\"ticket_description\": \"\","
                + "\"ticket_requester_email\": \"e@test.com\","
                + "\"ticket_requester_name\": \"E\","
                + "\"triggered_event\": \"status_changed\","
                + "\"ticket_status\": \"Open\""
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // Parsing: status_changed to Pending → IGNORED
    // -----------------------------------------------------------------------

    @Test
    void parse_statusChangedToPending_producesIgnored() {
        String body = "{"
                + "\"ticket_id\": 81,"
                + "\"ticket_subject\": \"Pending\","
                + "\"ticket_description\": \"\","
                + "\"ticket_requester_email\": \"f@test.com\","
                + "\"ticket_requester_name\": \"F\","
                + "\"triggered_event\": \"status_changed\","
                + "\"ticket_status\": \"Pending\""
                + "}";

        TicketEvent event = parser.parse(channelNoSecret, body);

        assertEquals(EventType.IGNORED, event.type());
    }

    // -----------------------------------------------------------------------
    // parseAndVerify: secret set + matching header → pass
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_secretSet_matchingHeader_passes() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Autotix-Webhook-Token", SECRET);

        String body = "{\"ticket_id\": 1, \"ticket_subject\": \"S\", \"ticket_description\": \"\","
                + "\"ticket_requester_email\": \"x@test.com\", \"ticket_requester_name\": \"X\","
                + "\"triggered_event\": \"ticket_created\", \"ticket_status\": \"Open\"}";

        TicketEvent event = parser.parseAndVerify(channelWithSecret, headers, body);

        assertNotNull(event);
        assertEquals(EventType.NEW_TICKET, event.type());
    }

    // -----------------------------------------------------------------------
    // parseAndVerify: secret set + missing header → reject
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_secretSet_missingHeader_throwsAuthException() {
        Map<String, String> headers = Collections.emptyMap();
        String body = "{\"ticket_id\": 2}";

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channelWithSecret, headers, body),
                "Missing token header should throw AuthException");
    }

    // -----------------------------------------------------------------------
    // parseAndVerify: secret set + mismatching header → reject
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_secretSet_mismatchingHeader_throwsAuthException() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Autotix-Webhook-Token", "wrongToken");

        String body = "{\"ticket_id\": 3}";

        assertThrows(AutotixException.AuthException.class,
                () -> parser.parseAndVerify(channelWithSecret, headers, body),
                "Wrong token should throw AuthException");
    }

    // -----------------------------------------------------------------------
    // parseAndVerify: secret unset → allow (warn in logs, no exception)
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_secretUnset_allowsWithoutToken() {
        Map<String, String> headers = Collections.emptyMap();
        String body = "{\"ticket_id\": 4, \"ticket_subject\": \"No secret\","
                + "\"ticket_description\": \"\", \"ticket_requester_email\": \"g@test.com\","
                + "\"ticket_requester_name\": \"G\", \"triggered_event\": \"ticket_created\","
                + "\"ticket_status\": \"Open\"}";

        // Should not throw
        assertDoesNotThrow(() -> parser.parseAndVerify(channelNoSecret, headers, body));
    }

    // -----------------------------------------------------------------------
    // parseAndVerify: case-insensitive header lookup
    // -----------------------------------------------------------------------

    @Test
    void parseAndVerify_caseInsensitiveHeader_passes() {
        Map<String, String> headers = new HashMap<>();
        // Lowercase variant
        headers.put("x-autotix-webhook-token", SECRET);

        String body = "{\"ticket_id\": 5, \"ticket_subject\": \"Lower\","
                + "\"ticket_description\": \"\", \"ticket_requester_email\": \"h@test.com\","
                + "\"ticket_requester_name\": \"H\", \"triggered_event\": \"ticket_created\","
                + "\"ticket_status\": \"Open\"}";

        assertDoesNotThrow(() -> parser.parseAndVerify(channelWithSecret, headers, body));
    }
}
