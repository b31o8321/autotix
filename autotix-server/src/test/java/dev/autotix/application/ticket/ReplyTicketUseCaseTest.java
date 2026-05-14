package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.MessageVisibility;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.infrastructure.formatter.ReplyFormatter;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
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
    @Mock private InboxEventPublisher inboxPublisher;
    @Mock private TicketActivityRepository activityRepository;

    private ReplyTicketUseCase useCase;

    private Channel channel;
    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new ReplyTicketUseCase(ticketRepository, channelRepository, replyFormatter,
                pluginRegistry, inboxPublisher, activityRepository);

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

        useCase.reply(ticketId, "**Hello**", "agent-user");

        verify(plugin).sendReply(eq(channel), eq(ticket), eq("<b>Hello</b>"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertEquals(2, saved.messages().size());
        assertEquals("**Hello**", saved.messages().get(1).content());
        assertEquals(MessageDirection.OUTBOUND, saved.messages().get(1).direction());
        assertEquals(MessageVisibility.PUBLIC, saved.messages().get(1).visibility());
        verify(inboxPublisher).publish(argThat(e -> e.kind == InboxEvent.Kind.AGENT_REPLIED));
    }

    @Test
    void aiAuthor_doesNotPublishAgentReplied() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(replyFormatter.format(ChannelType.EMAIL, "AI answer")).thenReturn("AI answer");
        when(pluginRegistry.get(PlatformType.ZENDESK)).thenReturn(plugin);
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.reply(ticketId, "AI answer", "ai");

        verify(plugin).sendReply(eq(channel), eq(ticket), eq("AI answer"));
        verifyNoInteractions(inboxPublisher);
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

        verify(ticketRepository, never()).save(any());
    }

    @Test
    void internalNote_doesNotCallPlugin_doesNotChangeStatus() {
        // Start with an OPEN ticket
        ticket.appendInbound(new Message(MessageDirection.INBOUND, "cust@test.com",
                "Another message", Instant.now()));
        // Now ticket status is OPEN
        assertEquals(TicketStatus.NEW, ticket.status()); // initial is NEW since only one inbound before
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);
        when(channelRepository.findById(any())).thenReturn(Optional.of(channel));

        TicketStatus statusBefore = ticket.status();
        useCase.reply(ticketId, "This is an internal note", "agent", true);

        // Plugin should NOT be called
        verifyNoInteractions(pluginRegistry);

        // Ticket should be saved with internal note message
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();

        // Check that last message is internal
        Message lastMsg = saved.messages().get(saved.messages().size() - 1);
        assertEquals(MessageVisibility.INTERNAL, lastMsg.visibility());
        assertEquals(MessageDirection.OUTBOUND, lastMsg.direction());
        assertEquals("This is an internal note", lastMsg.content());

        // Status must NOT have changed
        assertEquals(statusBefore, saved.status());

        // Activity must be logged as REPLIED_INTERNAL
        verify(activityRepository).save(argThat(
                a -> a.action().name().equals("REPLIED_INTERNAL")));
    }
}
