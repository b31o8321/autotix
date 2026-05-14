package dev.autotix.application.ticket;

import dev.autotix.domain.channel.ChannelId;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCloseSolvedTicketsSchedulerTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private InboxEventPublisher inboxPublisher;

    private AutoCloseSolvedTicketsScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AutoCloseSolvedTicketsScheduler(ticketRepository, inboxPublisher);
        // Set reopen window to 7 days (default)
        ReflectionTestUtils.setField(scheduler, "reopenWindowDays", 7);
    }

    private Ticket makeSolvedTicket(String id, Instant solvedAt) {
        Message msg = new Message(MessageDirection.INBOUND, "customer", "Hello", Instant.now());
        Ticket t = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-" + id, "Sub", "cust", msg);
        t.assignPersistedId(new TicketId(id));
        t.solve(solvedAt);
        return t;
    }

    @Test
    void solvedTicketsWithinWindow_areNotAutoClosedByScheduler() {
        // findSolvedBefore(cutoff) returns empty — the DB query already filters by cutoff
        when(ticketRepository.findSolvedBefore(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.autoCloseExpiredSolved();

        // No saves, no events
        verify(ticketRepository, never()).save(any());
        verifyNoInteractions(inboxPublisher);
    }

    @Test
    void solvedTicketsPastWindow_areAutoClosedAndEventPublished() {
        // Two tickets solved 8+ days ago (past 7-day window)
        Instant expiredSolveTime = Instant.now().minus(Duration.ofDays(8));
        Ticket t1 = makeSolvedTicket("10", expiredSolveTime);
        Ticket t2 = makeSolvedTicket("11", expiredSolveTime);

        when(ticketRepository.findSolvedBefore(any(Instant.class)))
                .thenReturn(Arrays.asList(t1, t2));
        when(ticketRepository.save(any())).thenAnswer(inv -> ((Ticket) inv.getArgument(0)).id());

        scheduler.autoCloseExpiredSolved();

        // Both tickets saved
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(2)).save(ticketCaptor.capture());
        List<Ticket> saved = ticketCaptor.getAllValues();
        assertEquals(TicketStatus.CLOSED, saved.get(0).status());
        assertNotNull(saved.get(0).closedAt());
        assertEquals(TicketStatus.CLOSED, saved.get(1).status());

        // STATUS_CHANGED published for each
        ArgumentCaptor<InboxEvent> eventCaptor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxPublisher, times(2)).publish(eventCaptor.capture());
        eventCaptor.getAllValues().forEach(e ->
                assertEquals(InboxEvent.Kind.STATUS_CHANGED, e.kind));
    }

    @Test
    void mixedTickets_onlyExpiredAreReturnedByRepo_andClosed() {
        // Simulate the repo correctly returning only expired tickets
        Ticket expired = makeSolvedTicket("20", Instant.now().minus(Duration.ofDays(10)));

        when(ticketRepository.findSolvedBefore(any(Instant.class)))
                .thenReturn(Collections.singletonList(expired));
        when(ticketRepository.save(any())).thenReturn(expired.id());

        scheduler.autoCloseExpiredSolved();

        verify(ticketRepository, times(1)).save(any());
        verify(inboxPublisher, times(1)).publish(any());
    }
}
