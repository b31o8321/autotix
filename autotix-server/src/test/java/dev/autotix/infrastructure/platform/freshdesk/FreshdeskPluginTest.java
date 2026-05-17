package dev.autotix.infrastructure.platform.freshdesk;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FreshdeskPlugin unit tests — mock FreshdeskClient + FreshdeskWebhookParser.
 */
class FreshdeskPluginTest {

    private FreshdeskClient mockClient;
    private FreshdeskWebhookParser mockParser;
    private FreshdeskPlugin plugin;
    private Channel channel;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(FreshdeskClient.class);
        mockParser = Mockito.mock(FreshdeskWebhookParser.class);
        plugin = new FreshdeskPlugin(mockClient, mockParser);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("domain", "acme");
        attrs.put("api_key", "testKey");
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);
        channel = Channel.rehydrate(
                new ChannelId("ch-fd-test"),
                PlatformType.FRESHDESK,
                ChannelType.EMAIL,
                "Test Freshdesk",
                "webhookToken999",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());

        ticket = Mockito.mock(Ticket.class);
        when(ticket.externalNativeId()).thenReturn("12345");
        when(ticket.id()).thenReturn(null);
    }

    // -----------------------------------------------------------------------
    // parseWebhook: delegates to parser.parseAndVerify
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_delegatesToParserParseAndVerify() {
        String rawBody = "{\"ticket_id\":1}";
        TicketEvent expected = new TicketEvent(
                new ChannelId("ch-fd-test"),
                EventType.NEW_TICKET, "1",
                "user@test.com", "User", "Subject", "body",
                Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockParser.parseAndVerify(eq(channel), any(), eq(rawBody))).thenReturn(expected);

        TicketEvent result = plugin.parseWebhook(channel, Collections.<String, String>emptyMap(), rawBody);

        assertSame(expected, result);
        verify(mockParser).parseAndVerify(eq(channel), any(), eq(rawBody));
    }

    // -----------------------------------------------------------------------
    // sendReply: calls replyToTicket once with correct args
    // -----------------------------------------------------------------------

    @Test
    void sendReply_callsReplyToTicketOnce() {
        plugin.sendReply(channel, ticket, "Thank you for your message.");

        verify(mockClient, times(1))
                .replyToTicket(channel.credential(), "12345", "Thank you for your message.");
    }

    @Test
    void sendReply_bubblesAutotixException() {
        doThrow(new AutotixException.IntegrationException("freshdesk", "API down"))
                .when(mockClient).replyToTicket(any(), any(), any());

        assertThrows(AutotixException.IntegrationException.class,
                () -> plugin.sendReply(channel, ticket, "reply"),
                "AutotixException should propagate unchanged");
    }

    @Test
    void sendReply_wrapsRawExceptionInIntegrationException() {
        doThrow(new RuntimeException("unexpected"))
                .when(mockClient).replyToTicket(any(), any(), any());

        assertThrows(AutotixException.IntegrationException.class,
                () -> plugin.sendReply(channel, ticket, "reply"),
                "Raw exceptions should be wrapped in IntegrationException");
    }

    // -----------------------------------------------------------------------
    // closeTicket: calls updateTicketStatus with code 5 (Closed)
    // -----------------------------------------------------------------------

    @Test
    void close_callsUpdateTicketStatusWithCode5() {
        plugin.close(channel, ticket);

        verify(mockClient, times(1))
                .updateTicketStatus(channel.credential(), "12345", 5);
    }

    @Test
    void close_bubblesAutotixException() {
        doThrow(new AutotixException.IntegrationException("freshdesk", "API down"))
                .when(mockClient).updateTicketStatus(any(), any(), anyInt());

        assertThrows(AutotixException.IntegrationException.class,
                () -> plugin.close(channel, ticket));
    }

    // -----------------------------------------------------------------------
    // healthCheck: delegates to client.ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_trueWhenPingSucceeds() {
        when(mockClient.ping(channel.credential())).thenReturn(true);

        assertTrue(plugin.healthCheck(channel.credential()));
        verify(mockClient).ping(channel.credential());
    }

    @Test
    void healthCheck_falseWhenPingFails() {
        when(mockClient.ping(channel.credential())).thenReturn(false);

        assertFalse(plugin.healthCheck(channel.credential()));
    }

    // -----------------------------------------------------------------------
    // platform / defaultChannelType
    // -----------------------------------------------------------------------

    @Test
    void platform_returnsFreshdesk() {
        assertEquals(PlatformType.FRESHDESK, plugin.platform());
    }

    @Test
    void defaultChannelType_returnsEmail() {
        assertEquals(ChannelType.EMAIL, plugin.defaultChannelType());
    }
}
