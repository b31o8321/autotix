package dev.autotix.infrastructure.platform.line;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LinePlugin unit tests — mocked LineClient + LineWebhookParser.
 */
class LinePluginTest {

    private LineClient mockClient;
    private LineWebhookParser mockParser;
    private LinePlugin plugin;
    private Channel channel;
    private Ticket mockTicket;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(LineClient.class);
        mockParser = Mockito.mock(LineWebhookParser.class);
        plugin = new LinePlugin(mockClient, mockParser);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("channel_access_token", "test-access-token");
        attrs.put("channel_secret", "test-secret");
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);

        channel = Channel.rehydrate(
                new ChannelId("ch-line-1"),
                PlatformType.LINE,
                ChannelType.CHAT,
                "Test LINE Channel",
                "webhookToken888",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());

        mockTicket = Mockito.mock(Ticket.class);
        when(mockTicket.externalNativeId()).thenReturn("Utestuser");
    }

    // -----------------------------------------------------------------------
    // sendReply: delegates to LineClient.pushText with correct args
    // -----------------------------------------------------------------------

    @Test
    void sendReply_callsPushTextWithCorrectArgs() {
        plugin.sendReply(channel, mockTicket, "Hello from support!");

        verify(mockClient, times(1))
                .pushText("test-access-token", "Utestuser", "Hello from support!");
    }

    // -----------------------------------------------------------------------
    // connect (healthCheck): delegates to LineClient.ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_callsPing() {
        when(mockClient.ping(any(LineCredentials.class))).thenReturn(true);

        boolean result = plugin.healthCheck(channel.credential());

        assertTrue(result);
        verify(mockClient, times(1)).ping(any(LineCredentials.class));
    }

    // -----------------------------------------------------------------------
    // parseWebhook: delegates to parser; returns first event from list
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_delegatesToParser_returnsFirstEvent() {
        TicketEvent expected = new TicketEvent(
                new ChannelId("ch-line-1"),
                EventType.NEW_TICKET,
                "Ufoo",
                "line:Ufoo",
                "",
                "Hello",
                "Hello",
                Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockParser.parseAndVerify(eq(channel), any(), any()))
                .thenReturn(Collections.singletonList(expected));

        TicketEvent result = plugin.parseWebhook(channel, Collections.emptyMap(), "{}");

        assertSame(expected, result);
        assertEquals(EventType.NEW_TICKET, result.type());
        assertEquals("line:Ufoo", result.customerIdentifier());
    }

    // -----------------------------------------------------------------------
    // parseWebhook: empty list from parser → returns IGNORED sentinel
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_emptyList_returnsIgnoredEvent() {
        when(mockParser.parseAndVerify(eq(channel), any(), any()))
                .thenReturn(Collections.<TicketEvent>emptyList());

        TicketEvent result = plugin.parseWebhook(channel, Collections.emptyMap(), "{\"events\":[]}");

        assertEquals(EventType.IGNORED, result.type());
    }

    // -----------------------------------------------------------------------
    // close: no-op (no exception thrown)
    // -----------------------------------------------------------------------

    @Test
    void close_isNoOp_doesNotThrow() {
        assertDoesNotThrow(() -> plugin.close(channel, mockTicket));
        verifyNoInteractions(mockClient);
    }

    // -----------------------------------------------------------------------
    // descriptor
    // -----------------------------------------------------------------------

    @Test
    void descriptor_isMarkedFunctional() {
        assertTrue(plugin.descriptor().functional);
        assertEquals(PlatformType.LINE, plugin.descriptor().platform);
        assertEquals(ChannelType.CHAT, plugin.descriptor().defaultChannelType);
    }
}
