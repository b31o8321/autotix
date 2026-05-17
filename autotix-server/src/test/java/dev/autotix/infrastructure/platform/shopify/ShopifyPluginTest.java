package dev.autotix.infrastructure.platform.shopify;

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
 * ShopifyPlugin unit tests — mock ShopifyClient + ShopifyWebhookParser.
 */
class ShopifyPluginTest {

    private ShopifyClient mockClient;
    private ShopifyWebhookParser mockParser;
    private ShopifyPlugin plugin;
    private Channel channelNoSecret;
    private Channel channelWithSecret;
    private Ticket orderTicket;
    private Ticket customerTicket;

    @BeforeEach
    void setUp() {
        mockClient = Mockito.mock(ShopifyClient.class);
        mockParser = Mockito.mock(ShopifyWebhookParser.class);
        plugin     = new ShopifyPlugin(mockClient, mockParser);

        Map<String, String> attrsNoSecret = new HashMap<>();
        attrsNoSecret.put("shop_domain", "test-store.myshopify.com");
        attrsNoSecret.put("admin_api_token", "shpat_test123");
        ChannelCredential credNoSecret = new ChannelCredential(null, null, null, attrsNoSecret);
        channelNoSecret = Channel.rehydrate(
                new ChannelId("ch-1"), PlatformType.SHOPIFY, ChannelType.EMAIL,
                "My Shopify", "wh-token-1", credNoSecret, true, true, Instant.now(), Instant.now());

        Map<String, String> attrsWithSecret = new HashMap<>(attrsNoSecret);
        attrsWithSecret.put("webhook_shared_secret", "s3cr3t");
        ChannelCredential credWithSecret = new ChannelCredential(null, null, null, attrsWithSecret);
        channelWithSecret = Channel.rehydrate(
                new ChannelId("ch-2"), PlatformType.SHOPIFY, ChannelType.EMAIL,
                "My Shopify+Secret", "wh-token-2", credWithSecret, true, true, Instant.now(), Instant.now());

        orderTicket = Mockito.mock(Ticket.class);
        when(orderTicket.externalNativeId()).thenReturn("12345");
        when(orderTicket.subject()).thenReturn("New order #1001");
        when(orderTicket.id()).thenReturn(null);

        customerTicket = Mockito.mock(Ticket.class);
        when(customerTicket.externalNativeId()).thenReturn("99999");
        when(customerTicket.subject()).thenReturn("New customer signup: Alice Smith");
        when(customerTicket.id()).thenReturn(null);
    }

    // -----------------------------------------------------------------------
    // parseWebhook: no secret → skips verification, delegates to parser
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_noSecret_delegatesToParserWithoutVerification() {
        String rawBody = "{\"id\":1}";
        TicketEvent expected = new TicketEvent(new ChannelId("ch-1"), EventType.NEW_TICKET, "1",
                "user@test.com", "User", "New order #1", "body", Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockParser.parse(eq(channelNoSecret), any(), eq(rawBody))).thenReturn(expected);

        TicketEvent result = plugin.parseWebhook(channelNoSecret, Collections.<String, String>emptyMap(), rawBody);

        assertSame(expected, result);
        verify(mockParser, never()).verifySignature(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // parseWebhook: bad signature → AuthException
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_badSignature_throwsAuthException() {
        when(mockParser.verifySignature(any(), any(), eq("s3cr3t"))).thenReturn(false);

        assertThrows(AutotixException.AuthException.class,
                () -> plugin.parseWebhook(channelWithSecret,
                        Collections.<String, String>emptyMap(), "{\"id\":2}"),
                "Invalid HMAC should throw AuthException");
        verify(mockParser, never()).parse(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // parseWebhook: valid signature → parse called
    // -----------------------------------------------------------------------

    @Test
    void parseWebhook_validSignature_delegatesToParser() {
        String rawBody = "{\"id\":3}";
        TicketEvent expected = new TicketEvent(new ChannelId("ch-2"), EventType.NEW_TICKET, "3",
                "cust@test.com", "Cust", "New order #999", "body", Instant.now(),
                Collections.<String, Object>emptyMap());

        when(mockParser.verifySignature(any(), eq(rawBody), eq("s3cr3t"))).thenReturn(true);
        when(mockParser.parse(eq(channelWithSecret), any(), eq(rawBody))).thenReturn(expected);

        TicketEvent result = plugin.parseWebhook(channelWithSecret, Collections.<String, String>emptyMap(), rawBody);

        assertSame(expected, result);
    }

    // -----------------------------------------------------------------------
    // sendReply: order ticket → appendOrderNote called
    // -----------------------------------------------------------------------

    @Test
    void sendReply_orderTicket_callsAppendOrderNote() {
        plugin.sendReply(channelNoSecret, orderTicket, "Thank you for your order");

        verify(mockClient).appendOrderNote(channelNoSecret.credential(), "12345", "Thank you for your order");
    }

    // -----------------------------------------------------------------------
    // sendReply: customer-signup ticket → skipped gracefully
    // -----------------------------------------------------------------------

    @Test
    void sendReply_customerSignupTicket_skipsGracefully() {
        // Should not throw and should not call appendOrderNote
        assertDoesNotThrow(() -> plugin.sendReply(channelNoSecret, customerTicket, "Welcome!"));
        verify(mockClient, never()).appendOrderNote(any(), any(), any());
    }

    // -----------------------------------------------------------------------
    // close: no-op — does not throw
    // -----------------------------------------------------------------------

    @Test
    void close_isNoOp() {
        assertDoesNotThrow(() -> plugin.close(channelNoSecret, orderTicket));
    }

    // -----------------------------------------------------------------------
    // healthCheck: delegates to client.ping
    // -----------------------------------------------------------------------

    @Test
    void healthCheck_delegatesToPing() {
        when(mockClient.ping(channelNoSecret.credential())).thenReturn(true);
        assertTrue(plugin.healthCheck(channelNoSecret.credential()));
        verify(mockClient).ping(channelNoSecret.credential());
    }

    @Test
    void healthCheck_returnsFalseWhenPingFails() {
        when(mockClient.ping(channelNoSecret.credential())).thenReturn(false);
        assertFalse(plugin.healthCheck(channelNoSecret.credential()));
    }
}
