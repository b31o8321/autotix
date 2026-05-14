package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
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
class SolveTicketUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private InboxEventPublisher inboxPublisher;
    @Mock private dev.autotix.domain.ticket.TicketActivityRepository activityRepository;

    private SolveTicketUseCase useCase;

    private Channel channel;
    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new SolveTicketUseCase(ticketRepository, channelRepository, inboxPublisher, activityRepository);

        ticketId = new TicketId("42");
        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Channel",
                "token",
                null,
                true,
                true,
                Instant.now(),
                Instant.now());

        Message inbound = new Message(MessageDirection.INBOUND, "customer@test.com",
                "I need help", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Subject",
                "customer@test.com", inbound);
        ticket.assignPersistedId(ticketId);
        ticket.changeStatus(TicketStatus.OPEN); // move to OPEN for solve
    }

    @Test
    void solve_transitionsToSolved_andPublishesEvent() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.solve(ticketId);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals(TicketStatus.SOLVED, ticketCaptor.getValue().status());
        assertNotNull(ticketCaptor.getValue().solvedAt());

        ArgumentCaptor<InboxEvent> eventCaptor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxPublisher).publish(eventCaptor.capture());
        assertEquals(InboxEvent.Kind.STATUS_CHANGED, eventCaptor.getValue().kind);
    }

    @Test
    void solve_idempotent_whenAlreadySolved() {
        // Pre-solve the ticket
        ticket.solve(Instant.now());

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        useCase.solve(ticketId);

        // No save or event should be triggered since ticket is already SOLVED
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(inboxPublisher);
    }

    @Test
    void solve_ticketNotFound_throws() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

        assertThrows(AutotixException.NotFoundException.class,
                () -> useCase.solve(ticketId));
    }

    @Test
    void solve_fromWaitingOnCustomer_succeeds() {
        // Put ticket in WAITING_ON_CUSTOMER
        ticket.appendOutbound(new Message(MessageDirection.OUTBOUND, "ai", "Hi there", Instant.now()));
        // ticket is now WAITING_ON_CUSTOMER

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.solve(ticketId);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals(TicketStatus.SOLVED, captor.getValue().status());
    }
}
