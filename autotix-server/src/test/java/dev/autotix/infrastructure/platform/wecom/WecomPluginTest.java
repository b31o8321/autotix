package dev.autotix.infrastructure.platform.wecom;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WeComPlugin unit tests — mocked WecomClient + WecomWebhookHandler.
 */
class WecomPluginTest {

    private WecomClient mockClient;
    private WecomWebhookHandler mockHandler;
    private WeComPlugin plugin;
    private Channel channel;
    private Ticket mockTicket;

    @BeforeEach
    void setUp() {
        mockClient  = Mockito.mock(WecomClient.class);
        mockHandler = Mockito.mock(WecomWebhookHandler.class);
        plugin = new WeComPlugin(mockClient, mockHandler);
        channel = buildChannel();

        mockTicket = Mockito.mock(Ticket.class);
        when(mockTicket.externalNativeId()).thenReturn("ext_user_001@wk_kfid_001");
    }

    // -----------------------------------------------------------------------
    // sendReply: calls WecomClient.sendText with correct args
    // -----------------------------------------------------------------------

    @Test
    void sendReply_callsSendTextWithCorrectArgs() {
        when(mockClient.getAccessToken("wwCORPIDtest", "test_secret")).thenReturn("ACCESS_TOKEN");

        plugin.sendReply(channel, mockTicket, "Hello customer!");

        verify(mockClient, times(1)).getAccessToken("wwCORPIDtest", "test_secret");
        verify(mockClient, times(1)).sendText("ACCESS_TOKEN", "ext_user_001", "wk_kfid_001", "Hello customer!");
    }

    @Test
    void sendReply_blankExternalId_throwsValidationException() {
        when(mockTicket.externalNativeId()).thenReturn("");
        assertThrows(AutotixException.ValidationException.class,
                () -> plugin.sendReply(channel, mockTicket, "Hello!"));
    }

    @Test
    void sendReply_noAtSign_usesCredentialOpenKfId() {
        when(mockTicket.externalNativeId()).thenReturn("ext_user_only");
        when(mockClient.getAccessToken(anyString(), anyString())).thenReturn("AT");

        plugin.sendReply(channel, mockTicket, "Reply text");

        verify(mockClient, times(1)).sendText("AT", "ext_user_only", "wk_open_kfid_default", "Reply text");
    }

    // -----------------------------------------------------------------------
    // healthCheck: delegates to WecomClient.ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_callsPing() {
        when(mockClient.ping("wwCORPIDtest", "test_secret")).thenReturn(true);

        assertTrue(plugin.healthCheck(channel.credential()));
        verify(mockClient, times(1)).ping("wwCORPIDtest", "test_secret");
    }

    @Test
    void healthCheck_bubblesPingException() {
        when(mockClient.ping(anyString(), anyString()))
                .thenThrow(new AutotixException.AuthException("WeCom auth failed"));

        assertThrows(AutotixException.AuthException.class,
                () -> plugin.healthCheck(channel.credential()));
    }

    // -----------------------------------------------------------------------
    // parseWebhook: delegates to handler; first event returned
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_delegatesToHandler_returnsFirstEvent() {
        TicketEvent expected = new TicketEvent(
                new ChannelId("ch-wecom-1"),
                EventType.NEW_TICKET,
                "ext_user@kfid",
                "wecom:ext_user",
                "",
                "Hello",
                "Hello from WeCom",
                Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockHandler.handlePost(eq(channel), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(expected));

        Map<String, String> headers = new HashMap<>();
        headers.put("x-wecom-msg-signature", "sig");
        headers.put("x-wecom-timestamp", "ts");
        headers.put("x-wecom-nonce", "nc");

        TicketEvent result = plugin.parseWebhook(channel, headers, "<xml/>");
        assertSame(expected, result);
        assertEquals(EventType.NEW_TICKET, result.type());
    }

    @Test
    void parseWebhook_emptyList_returnsIgnoredEvent() {
        when(mockHandler.handlePost(any(), any(), any(), any(), any()))
                .thenReturn(Collections.<TicketEvent>emptyList());

        TicketEvent result = plugin.parseWebhook(channel, Collections.<String, String>emptyMap(), "<xml/>");
        assertEquals(EventType.IGNORED, result.type());
    }

    // -----------------------------------------------------------------------
    // descriptor
    // -----------------------------------------------------------------------

    @Test
    void descriptor_isMarkedFunctional() {
        assertTrue(plugin.descriptor().functional);
        assertEquals(PlatformType.WECOM, plugin.descriptor().platform);
        assertEquals(ChannelType.CHAT, plugin.descriptor().defaultChannelType);
        assertNotNull(plugin.descriptor().setupGuide);
    }

    @Test
    void descriptor_hasExpectedAuthFields() {
        java.util.List<dev.autotix.domain.channel.PlatformDescriptor.AuthField> fields = plugin.descriptor().authFields;
        assertEquals(5, fields.size(), "Should have 5 auth fields");
        assertTrue(fields.stream().anyMatch(f -> "corpid".equals(f.key)));
        assertTrue(fields.stream().anyMatch(f -> "secret".equals(f.key)));
        assertTrue(fields.stream().anyMatch(f -> "token".equals(f.key)));
        assertTrue(fields.stream().anyMatch(f -> "encoding_aes_key".equals(f.key)));
        assertTrue(fields.stream().anyMatch(f -> "open_kfid".equals(f.key)));
    }

    // -----------------------------------------------------------------------
    // close: no-op
    // -----------------------------------------------------------------------

    @Test
    void close_isNoOp_doesNotThrow() {
        assertDoesNotThrow(() -> plugin.close(channel, mockTicket));
        verifyNoInteractions(mockClient);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Channel buildChannel() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("corpid",           "wwCORPIDtest");
        attrs.put("secret",           "test_secret");
        attrs.put("token",            "test_token");
        attrs.put("encoding_aes_key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        attrs.put("open_kfid",        "wk_open_kfid_default");
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);
        return Channel.rehydrate(
                new ChannelId("ch-wecom-1"),
                PlatformType.WECOM,
                ChannelType.CHAT,
                "Test WeCom Channel",
                "webhook_token_wecom",
                cred,
                true,
                true,
                Instant.now(),
                Instant.now());
    }
}
