package dev.autotix.infrastructure.platform.livechat;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.infrastructure.livechat.LiveChatSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiveChatPlugin.
 * Verifies that sendReply and close delegate correctly to LiveChatSessionRegistry.
 */
class LiveChatPluginTest {

    private LiveChatSessionRegistry registry;
    private LiveChatPlugin plugin;

    @BeforeEach
    void setUp() {
        registry = mock(LiveChatSessionRegistry.class);
        plugin = new LiveChatPlugin(registry);
    }

    @Test
    void platform_is_LIVECHAT() {
        assertEquals(PlatformType.LIVECHAT, plugin.platform());
    }

    @Test
    void parseWebhook_throws_unsupported() {
        Channel channel = mockChannel();
        assertThrows(UnsupportedOperationException.class,
                () -> plugin.parseWebhook(channel, Collections.emptyMap(), "{}"));
    }

    @Test
    void healthCheck_always_true() {
        assertTrue(plugin.healthCheck(null));
    }

    @Test
    void sendReply_pushes_message_frame_to_registry() {
        Channel channel = mockChannel();
        Ticket ticket = mockTicketWithOutboundAuthor("ai");

        plugin.sendReply(channel, ticket, "Hello, I can help!");

        verify(registry).pushToTicket(eq(ticket.id()), argThat(json ->
                json.contains("\"type\"") && json.contains("\"content\"") && json.contains("Hello, I can help!")));
    }

    @Test
    void sendReply_uses_ai_author_when_last_outbound_is_ai() {
        Channel channel = mockChannel();
        Ticket ticket = mockTicketWithOutboundAuthor("ai");

        plugin.sendReply(channel, ticket, "AI says hi");

        verify(registry).pushToTicket(eq(ticket.id()), argThat(json ->
                json.contains("\"type\":\"message\"")));
    }

    @Test
    void sendReply_uses_agent_author_when_last_outbound_is_human() {
        Channel channel = mockChannel();
        Ticket ticket = mockTicketWithOutboundAuthor("agent:john");

        plugin.sendReply(channel, ticket, "Human says hi");

        verify(registry).pushToTicket(eq(ticket.id()), argThat(json ->
                json.contains("\"type\":\"agent_message\"")));
    }

    @Test
    void close_pushes_CLOSED_status() {
        Channel channel = mockChannel();
        Ticket ticket = mockTicketWithOutboundAuthor("ai");

        plugin.close(channel, ticket);

        verify(registry).pushStatus(ticket.id(), "CLOSED");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Channel mockChannel() {
        Channel c = mock(Channel.class);
        when(c.id()).thenReturn(new ChannelId("ch-lc-1"));
        when(c.platform()).thenReturn(PlatformType.LIVECHAT);
        return c;
    }

    private Ticket mockTicketWithOutboundAuthor(String author) {
        Ticket t = mock(Ticket.class);
        when(t.id()).thenReturn(new TicketId("ticket-lc-1"));
        Message outbound = new Message(MessageDirection.OUTBOUND, author, "reply text", Instant.now());
        when(t.messages()).thenReturn(Collections.singletonList(outbound));
        return t;
    }
}
