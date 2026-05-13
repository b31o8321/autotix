package dev.autotix.domain.channel;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for Channel aggregate.
 * No Spring / DB involved.
 */
class ChannelTest {

    @Test
    void newInstance_createsDisabledChannelWithRandomNonEmptyWebhookToken() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk");

        assertNotNull(channel);
        assertFalse(channel.isEnabled());
        assertFalse(channel.isAutoReplyEnabled());
        assertNotNull(channel.webhookToken());
        assertFalse(channel.webhookToken().isEmpty());
        assertEquals(32, channel.webhookToken().length()); // UUID without dashes = 32 chars
        assertNull(channel.credential());
        assertNull(channel.id());
        assertNotNull(channel.createdAt());
    }

    @Test
    void connect_setsCredentialAndEnablesChannel() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk");
        assertFalse(channel.isEnabled());

        ChannelCredential cred = new ChannelCredential("access-token-123", null, null, null);
        channel.connect(cred);

        assertTrue(channel.isEnabled());
        assertNotNull(channel.credential());
        assertEquals("access-token-123", channel.credential().accessToken());
    }

    @Test
    void disconnect_clearsCredentialAndDisablesChannel() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk");
        ChannelCredential cred = new ChannelCredential("access-token-123", null, null, null);
        channel.connect(cred);
        assertTrue(channel.isEnabled());

        channel.disconnect();

        assertFalse(channel.isEnabled());
        // credential cleared on disconnect
        assertNull(channel.credential());
    }

    @Test
    void rotateWebhookToken_yieldsDifferentValue() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk");
        String original = channel.webhookToken();

        channel.rotateWebhookToken();
        String rotated = channel.webhookToken();

        assertNotNull(rotated);
        assertFalse(rotated.isEmpty());
        assertNotEquals(original, rotated);
    }

    @Test
    void channelCredential_isExpired_withNullExpiresAt_returnsFalse() {
        ChannelCredential cred = new ChannelCredential("token", null, null, null);
        assertFalse(cred.isExpired(Instant.now()));
    }

    @Test
    void channelCredential_isExpired_withPastExpiresAt_returnsTrue() {
        Instant past = Instant.now().minusSeconds(3600);
        ChannelCredential cred = new ChannelCredential("token", null, past, null);
        assertTrue(cred.isExpired(Instant.now()));
    }

    @Test
    void setAutoReply_togglesFlag() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "My Zendesk");
        assertFalse(channel.isAutoReplyEnabled());

        channel.setAutoReply(true);
        assertTrue(channel.isAutoReplyEnabled());

        channel.setAutoReply(false);
        assertFalse(channel.isAutoReplyEnabled());
    }
}
