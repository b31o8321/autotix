package dev.autotix.infrastructure.platform.zendesk;

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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ZendeskPlugin unit tests — mock ZendeskClient + ZendeskWebhookParser.
 */
class ZendeskPluginTest {

    private ZendeskClient mockClient;
    private ZendeskWebhookParser mockParser;
    private ZendeskPlugin plugin;
    private Channel channel;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(ZendeskClient.class);
        mockParser = Mockito.mock(ZendeskWebhookParser.class);
        plugin     = new ZendeskPlugin(mockClient, mockParser);

        ChannelCredential credential = new ChannelCredential(null, null, null, buildAttrs("myco", "agent@myco.com", "tok123", null));
        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "My Zendesk",
                "webhookxyz",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());

        ticket = Mockito.mock(Ticket.class);
        when(ticket.externalNativeId()).thenReturn("42");
    }

    // -----------------------------------------------------------------------
    // parseWebhook: bad signature → AuthException
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_badSignature_throwsAuthException() {
        // Channel with a webhook_secret
        ChannelCredential credWithSecret = new ChannelCredential(null, null, null,
                buildAttrs("myco", "agent@myco.com", "tok123", "s3cr3t"));
        Channel channelWithSecret = Channel.rehydrate(
                new ChannelId("ch-2"), PlatformType.ZENDESK, ChannelType.EMAIL,
                "Zendesk+Secret", "wh2", credWithSecret, true, true, Instant.now(), Instant.now());

        when(mockParser.verifySignature(any(), any(), eq("s3cr3t"))).thenReturn(false);

        assertThrows(AutotixException.AuthException.class,
                () -> plugin.parseWebhook(channelWithSecret,
                        Collections.<String, String>emptyMap(),
                        "{\"type\":\"zen:event-type:ticket.created\"}"),
                "Bad signature should throw AuthException");
        verify(mockParser, never()).parse(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // parseWebhook: no secret → signature skipped, parse called
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_noSecret_delegatesToParser() {
        String rawBody = "{\"type\":\"zen:event-type:comment.created\"}";
        TicketEvent expectedEvent = new TicketEvent(
                new ChannelId("ch-1"), EventType.NEW_MESSAGE, "42",
                "user@example.com", "User", null, "Hello", Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockParser.parse(eq(channel), any(), eq(rawBody))).thenReturn(expectedEvent);

        TicketEvent result = plugin.parseWebhook(channel, Collections.<String, String>emptyMap(), rawBody);

        assertSame(expectedEvent, result);
        verify(mockParser, never()).verifySignature(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // sendReply: delegates to client.postComment
    // -----------------------------------------------------------------------

    @Test
    void sendReply_callsPostComment() {
        plugin.sendReply(channel, ticket, "<p>Thank you</p>");

        verify(mockClient).postComment(channel.credential(), "42", "<p>Thank you</p>");
    }

    // -----------------------------------------------------------------------
    // close: calls updateStatus with "solved"
    // -----------------------------------------------------------------------

    @Test
    void close_callsUpdateStatusWithSolved() {
        plugin.close(channel, ticket);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockClient).updateStatus(eq(channel.credential()), eq("42"), statusCaptor.capture());
        assertEquals("solved", statusCaptor.getValue());
    }

    // -----------------------------------------------------------------------
    // healthCheck: delegates to client.ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_delegatesToPing() {
        ChannelCredential cred = channel.credential();
        when(mockClient.ping(cred)).thenReturn(true);

        assertTrue(plugin.healthCheck(cred));
        verify(mockClient).ping(cred);
    }

    @Test
    void healthCheck_falseWhenPingFails() {
        ChannelCredential cred = channel.credential();
        when(mockClient.ping(cred)).thenReturn(false);

        assertFalse(plugin.healthCheck(cred));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Map<String, String> buildAttrs(String subdomain, String email,
                                                   String apiToken, String webhookSecret) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("subdomain", subdomain);
        m.put("email", email);
        m.put("api_token", apiToken);
        if (webhookSecret != null) {
            m.put("webhook_secret", webhookSecret);
        }
        return m;
    }
}
