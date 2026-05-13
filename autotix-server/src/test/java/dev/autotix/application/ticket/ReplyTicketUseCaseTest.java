package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.formatter.ReplyFormatter;
import dev.autotix.infrastructure.platform.PluginRegistry;
import dev.autotix.infrastructure.platform.TicketPlatformPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplyTicketUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private ReplyFormatter replyFormatter;
    @Mock private PluginRegistry pluginRegistry;
    @Mock private TicketPlatformPlugin plugin;

    private ReplyTicketUseCase useCase;

    private Channel channel;
    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new ReplyTicketUseCase(ticketRepository, channelRepository, replyFormatter, pluginRegistry);

        ticketId = new TicketId("5");
        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test",
                "tok",
                null,
                true,
                true,
                Instant.now(),
                Instant.now());

        Message inbound = new Message(MessageDirection.INBOUND, "cust@test.com",
                "Hello", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-5", "Sub",
                "cust@test.com", inbound);
        ticket.assignPersistedId(ticketId);
    }

    @Test
    void sendReply_callsPluginWithFormattedReply_andAppendsOutbound() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(replyFormatter.format(ChannelType.EMAIL, "**Hello**")).thenReturn("<b>Hello</b>");
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.reply(ticketId, "**Hello**", "ai");

        // Plugin was called with the formatted (HTML) reply
        verify(plugin).sendReply(eq(channel), eq(ticket), eq("<b>Hello</b>"));

        // Ticket has outbound message with the original markdown (not formatted)
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertEquals(2, saved.messages().size());
        assertEquals("**Hello**", saved.messages().get(1).content());
        assertEquals(MessageDirection.OUTBOUND, saved.messages().get(1).direction());
    }

    @Test
    void pluginThrows_wrapsAsIntegrationException() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(replyFormatter.format(any(), any())).thenReturn("formatted");
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        doThrow(new RuntimeException("network error")).when(plugin).sendReply(any(), any(), any());

        assertThrows(AutotixException.IntegrationException.class,
                () -> useCase.reply(ticketId, "reply", "ai"));

        // Ticket should NOT be saved since the reply failed
        verify(ticketRepository, never()).save(any());
    }
}
