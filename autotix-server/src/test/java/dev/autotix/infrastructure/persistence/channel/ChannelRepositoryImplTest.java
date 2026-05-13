package dev.autotix.infrastructure.persistence.channel;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for ChannelRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChannelRepositoryImplTest {

    @Autowired
    private ChannelRepository channelRepository;

    @Test
    void save_roundTrips_attributesJsonMapCorrectly() {
        Channel channel = Channel.newInstance(PlatformType.ZENDESK, ChannelType.EMAIL, "Zendesk Test");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("subdomain", "acme");
        attributes.put("apiKey", "secret-key-123");
        ChannelCredential cred = new ChannelCredential("access-abc", "refresh-xyz", null, attributes);
        channel.connect(cred);

        ChannelId savedId = channelRepository.save(channel);
        assertNotNull(savedId);

        Optional<Channel> loaded = channelRepository.findById(savedId);
        assertTrue(loaded.isPresent());

        Channel c = loaded.get();
        assertTrue(c.isEnabled());
        assertNotNull(c.credential());
        assertEquals("access-abc", c.credential().accessToken());
        assertEquals("refresh-xyz", c.credential().refreshToken());
        assertNotNull(c.credential().attributes());
        assertEquals("acme", c.credential().attributes().get("subdomain"));
        assertEquals("secret-key-123", c.credential().attributes().get("apiKey"));
        assertEquals(PlatformType.ZENDESK, c.platform());
        assertEquals(ChannelType.EMAIL, c.type());
        assertEquals("Zendesk Test", c.displayName());
    }

    @Test
    void findByWebhookToken_returnsNothingForDisabledChannel() {
        Channel channel = Channel.newInstance(PlatformType.FRESHDESK, ChannelType.EMAIL, "FD Disabled");
        // Channel starts disabled — save without connecting
        channelRepository.save(channel);

        String token = channel.webhookToken();
        Optional<Channel> found = channelRepository.findByWebhookToken(PlatformType.FRESHDESK, token);

        // findByWebhookToken only returns enabled channels
        assertFalse(found.isPresent());
    }

    @Test
    void delete_isSoft_channelStillInFindAll() {
        Channel channel = Channel.newInstance(PlatformType.GORGIAS, ChannelType.CHAT, "Gorgias Chat");
        ChannelCredential cred = new ChannelCredential("tok", null, null, null);
        channel.connect(cred);
        ChannelId savedId = channelRepository.save(channel);

        channelRepository.delete(savedId);

        // Should still appear in findAll (admin view includes disabled)
        List<Channel> all = channelRepository.findAll();
        boolean found = false;
        for (Channel c : all) {
            if (c.id().equals(savedId)) {
                found = true;
                assertFalse(c.isEnabled(), "Soft-deleted channel should have enabled=false");
                break;
            }
        }
        assertTrue(found, "Soft-deleted channel must still be in findAll");

        // But findByWebhookToken should not return it
        Optional<Channel> byToken = channelRepository.findByWebhookToken(
                PlatformType.GORGIAS, channel.webhookToken());
        assertFalse(byToken.isPresent());
    }

    @Test
    void findByWebhookToken_returnsEnabledChannel() {
        Channel channel = Channel.newInstance(PlatformType.LIVECHAT, ChannelType.CHAT, "LiveChat");
        ChannelCredential cred = new ChannelCredential("live-token", null, null, null);
        channel.connect(cred);
        ChannelId savedId = channelRepository.save(channel);

        String token = channel.webhookToken();
        Optional<Channel> found = channelRepository.findByWebhookToken(PlatformType.LIVECHAT, token);

        assertTrue(found.isPresent());
        assertEquals(savedId, found.get().id());
        assertTrue(found.get().isEnabled());
    }
}
