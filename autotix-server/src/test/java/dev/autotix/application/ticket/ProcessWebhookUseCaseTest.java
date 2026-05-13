package dev.autotix.application.ticket;

import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketDomainService;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.infrastructure.infra.idempotency.IdempotencyStore;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessWebhookUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private QueueProvider queueProvider;

    private TicketDomainService ticketDomainService;
    private ProcessWebhookUseCase useCase;

    private Channel channel;

    @BeforeEach
    void setUp() {
        ticketDomainService = new TicketDomainService();
        useCase = new ProcessWebhookUseCase(ticketRepository, idempotencyStore,
                queueProvider, ticketDomainService);

        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Channel",
                "token123",
                null,
                true,
                true, // autoReplyEnabled
                Instant.now(),
                Instant.now());
    }

    @Test
    void firstTime_createsTicket_andQueuesAi_whenAutoReplyEnabled() {
        Instant now = Instant.now();
        TicketEvent event = new TicketEvent(
                new ChannelId("ch-1"),
                EventType.NEW_TICKET,
                "ext-ticket-1",
                "customer@example.com",
                "Alice",
                "Help me",
                "I need help",
                now,
                Collections.emptyMap());

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());

        // Simulate save setting an id on the ticket
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("1"));
            return new TicketId("1");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, event);

        // Ticket was saved
        verify(ticketRepository).save(any(Ticket.class));
        // AI dispatch was queued
        verify(queueProvider).publish(eq("ai.dispatch"), eq("1"));
    }

    @Test
    void idempotentReplay_isNoOp() {
        Instant now = Instant.now();
        TicketEvent event = new TicketEvent(
                new ChannelId("ch-1"),
                EventType.NEW_TICKET,
                "ext-ticket-2",
                "customer@example.com",
                "Bob",
                "Subject",
                "Body",
                now,
                Collections.emptyMap());

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(false); // duplicate

        useCase.handle(channel, event);

        verifyNoInteractions(ticketRepository, queueProvider);
    }

    @Test
    void existingTicket_appendsInbound_andSaves() {
        Instant now = Instant.now();
        TicketEvent event = new TicketEvent(
                new ChannelId("ch-1"),
                EventType.NEW_MESSAGE,
                "ext-ticket-3",
                "customer@example.com",
                "Carol",
                "Subject",
                "Follow up message",
                now,
                Collections.emptyMap());

        // Build an existing ticket with a prior inbound message
        Message originalMsg = new Message(MessageDirection.INBOUND, "customer@example.com",
                "Original message", now.minusSeconds(60));
        Ticket existingTicket = Ticket.openFromInbound(
                new ChannelId("ch-1"), "ext-ticket-3", "Subject", "customer@example.com", originalMsg);
        existingTicket.assignPersistedId(new TicketId("99"));

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-ticket-3")))
                .thenReturn(Optional.of(existingTicket));
        when(ticketRepository.save(any())).thenReturn(new TicketId("99"));

        useCase.handle(channel, event);

        // Ticket was saved with the new message appended
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertEquals(2, saved.messages().size(), "Existing ticket should have 2 messages after append");
        assertEquals("Follow up message", saved.messages().get(1).content());
    }

    @Test
    void autoReplyDisabled_doesNotQueue() {
        Channel noAutoReply = Channel.rehydrate(
                new ChannelId("ch-2"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "No-AI Channel",
                "token-noai",
                null,
                true,
                false, // autoReplyEnabled = false
                Instant.now(),
                Instant.now());

        Instant now = Instant.now();
        TicketEvent event = new TicketEvent(
                new ChannelId("ch-2"),
                EventType.NEW_TICKET,
                "ext-ticket-4",
                "customer@example.com",
                "Dave",
                "Subject",
                "Body",
                now,
                Collections.emptyMap());

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("2"));
            return new TicketId("2");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(noAutoReply, event);

        verify(ticketRepository).save(any(Ticket.class));
        verifyNoInteractions(queueProvider);
    }
}
