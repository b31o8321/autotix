package dev.autotix.infrastructure.platform;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelCredential;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Ticket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure JUnit tests for PluginRegistry — no Spring context.
 */
class PluginRegistryTest {

    // -----------------------------------------------------------------------
    // Happy path: find each plugin by platform
    // -----------------------------------------------------------------------

    @Test
    void get_returnsCorrectPlugin_byPlatform() {
        TicketPlatformPlugin zendesk = new StubPlugin(PlatformType.ZENDESK, ChannelType.EMAIL);
        TicketPlatformPlugin freshdesk = new StubPlugin(PlatformType.FRESHDESK, ChannelType.EMAIL);
        TicketPlatformPlugin line = new StubPlugin(PlatformType.LINE, ChannelType.CHAT);

        PluginRegistry registry = new PluginRegistry(Arrays.asList(zendesk, freshdesk, line));

        assertSame(zendesk, registry.get(PlatformType.ZENDESK));
        assertSame(freshdesk, registry.get(PlatformType.FRESHDESK));
        assertSame(line, registry.get(PlatformType.LINE));
    }

    // -----------------------------------------------------------------------
    // Duplicate platform -> IllegalStateException
    // -----------------------------------------------------------------------

    @Test
    void duplicatePlatform_throwsAtConstruction() {
        TicketPlatformPlugin a = new StubPlugin(PlatformType.ZENDESK, ChannelType.EMAIL);
        TicketPlatformPlugin b = new StubPlugin(PlatformType.ZENDESK, ChannelType.EMAIL);

        assertThrows(IllegalStateException.class,
                () -> new PluginRegistry(Arrays.asList(a, b)));
    }

    // -----------------------------------------------------------------------
    // get() for unregistered platform -> NotFoundException
    // -----------------------------------------------------------------------

    @Test
    void get_unregisteredPlatform_throwsNotFoundException() {
        TicketPlatformPlugin zendesk = new StubPlugin(PlatformType.ZENDESK, ChannelType.EMAIL);
        PluginRegistry registry = new PluginRegistry(Collections.singletonList(zendesk));

        assertThrows(AutotixException.NotFoundException.class,
                () -> registry.get(PlatformType.LINE));
    }

    // -----------------------------------------------------------------------
    // all() returns unmodifiable list of all plugins
    // -----------------------------------------------------------------------

    @Test
    void all_returnsAllPlugins() {
        TicketPlatformPlugin zendesk = new StubPlugin(PlatformType.ZENDESK, ChannelType.EMAIL);
        TicketPlatformPlugin line = new StubPlugin(PlatformType.LINE, ChannelType.CHAT);

        PluginRegistry registry = new PluginRegistry(Arrays.asList(zendesk, line));
        List<TicketPlatformPlugin> all = registry.all();

        assertEquals(2, all.size());
        assertTrue(all.contains(zendesk));
        assertTrue(all.contains(line));
    }

    // -----------------------------------------------------------------------
    // Stub implementation
    // -----------------------------------------------------------------------

    private static final class StubPlugin implements TicketPlatformPlugin {
        private final PlatformType platform;
        private final ChannelType channelType;

        StubPlugin(PlatformType platform, ChannelType channelType) {
            this.platform = platform;
            this.channelType = channelType;
        }

        @Override public PlatformType platform() { return platform; }
        @Override public ChannelType defaultChannelType() { return channelType; }

        @Override
        public TicketEvent parseWebhook(Channel channel, Map<String, String> headers, String rawBody) {
            throw new UnsupportedOperationException("stub");
        }
        @Override
        public void sendReply(Channel channel, Ticket ticket, String formattedReply) {
            throw new UnsupportedOperationException("stub");
        }
        @Override
        public void close(Channel channel, Ticket ticket) {
            throw new UnsupportedOperationException("stub");
        }
        @Override
        public boolean healthCheck(ChannelCredential credential) {
            return true;
        }
    }
}
