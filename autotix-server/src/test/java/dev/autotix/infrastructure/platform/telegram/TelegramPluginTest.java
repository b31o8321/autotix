package dev.autotix.infrastructure.platform.telegram;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TelegramPlugin unit tests — mock TelegramClient + TelegramWebhookParser.
 */
class TelegramPluginTest {

    private TelegramClient mockClient;
    private TelegramWebhookParser mockParser;
    private TelegramPlugin plugin;
    private Channel channel;
    private Ticket ticket;

    private static final String BOT_TOKEN = "111:TestToken";
    private static final String CHAT_ID = "987654321";

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(TelegramClient.class);
        mockParser = Mockito.mock(TelegramWebhookParser.class);
        plugin = new TelegramPlugin(mockClient, mockParser);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("bot_token", BOT_TOKEN);
        ChannelCredential credential = new ChannelCredential(null, null, null, attrs);
        channel = Channel.rehydrate(
                new ChannelId("ch-tg-test"),
                PlatformType.TELEGRAM,
                ChannelType.CHAT,
                "Test Telegram",
                "webhookTokenXYZ",
                credential,
                true,
                true,
                Instant.now(),
                Instant.now());

        ticket = Mockito.mock(Ticket.class);
        when(ticket.externalNativeId()).thenReturn(CHAT_ID);
    }

    // -----------------------------------------------------------------------
    // sendReply: calls TelegramClient.sendMessage with correct args
    // -----------------------------------------------------------------------

    @Test
    void sendReply_callsSendMessageWithCorrectArgs() {
        plugin.sendReply(channel, ticket, "Hello from Autotix!");

        verify(mockClient).sendMessage(eq(BOT_TOKEN), eq(CHAT_ID), eq("Hello from Autotix!"));
    }

    @Test
    void sendReply_throwsValidationExceptionWhenChatIdMissing() {
        when(ticket.externalNativeId()).thenReturn("");

        assertThrows(AutotixException.ValidationException.class,
                () -> plugin.sendReply(channel, ticket, "Hello"),
                "Empty chat_id should throw ValidationException");
    }

    // -----------------------------------------------------------------------
    // healthCheck: delegates to ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_trueWhenPingSucceeds() {
        when(mockClient.ping(BOT_TOKEN)).thenReturn(true);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("bot_token", BOT_TOKEN);
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);

        assertTrue(plugin.healthCheck(cred));
        verify(mockClient).ping(BOT_TOKEN);
    }

    @Test
    void healthCheck_falseWhenPingFails() {
        when(mockClient.ping(BOT_TOKEN)).thenReturn(false);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("bot_token", BOT_TOKEN);
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);

        assertFalse(plugin.healthCheck(cred));
    }

    @Test
    void healthCheck_throwsValidationExceptionWhenTokenMissing() {
        Map<String, String> attrs = new HashMap<>();
        // no bot_token
        ChannelCredential cred = new ChannelCredential(null, null, null, attrs);

        assertThrows(AutotixException.ValidationException.class,
                () -> plugin.healthCheck(cred),
                "Missing bot_token should throw ValidationException");
    }

    // -----------------------------------------------------------------------
    // close: no-op (should not throw)
    // -----------------------------------------------------------------------

    @Test
    void close_isNoOp() {
        assertDoesNotThrow(() -> plugin.close(channel, ticket));
        verifyNoInteractions(mockClient);
    }

    // -----------------------------------------------------------------------
    // platform and defaultChannelType
    // -----------------------------------------------------------------------

    @Test
    void platform_returnsTelegram() {
        assertEquals(PlatformType.TELEGRAM, plugin.platform());
    }

    @Test
    void defaultChannelType_returnsChat() {
        assertEquals(ChannelType.CHAT, plugin.defaultChannelType());
    }
}
