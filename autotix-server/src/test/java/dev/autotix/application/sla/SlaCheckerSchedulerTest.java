package dev.autotix.application.sla;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
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
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SlaCheckerScheduler.
 */
@ExtendWith(MockitoExtension.class)
class SlaCheckerSchedulerTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketActivityRepository activityRepository;
    @Mock
    private InboxEventPublisher inboxEventPublisher;

    private SlaCheckerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SlaCheckerScheduler(ticketRepository, activityRepository, inboxEventPublisher);
    }

    private Ticket makeOpenTicket(String externalId, boolean breached) {
        Message msg = new Message(MessageDirection.INBOUND, "customer", "Hello", Instant.now().minusSeconds(3600));
        Ticket t = Ticket.openFromInbound(new ChannelId("ch-1"), externalId, "Sub", "customer", msg);
        t.assignPersistedId(new TicketId("1"));
        t.applySlaDeadlines(
                Instant.now().minusSeconds(60),   // firstResponseDue in the past
                Instant.now().minusSeconds(30)    // resolutionDue in the past
        );
        if (breached) {
            t.markSlaBreached();
        }
        return t;
    }

    @Test
    void overdueTicket_markedBreached_activityLogged_eventPublished() {
        Ticket overdue = makeOpenTicket("ext-1", false);
        when(ticketRepository.findOverdue(any())).thenReturn(Collections.singletonList(overdue));
        when(ticketRepository.save(any())).thenReturn(overdue.id());

        scheduler.checkOverdue();

        assertTrue(overdue.slaBreached());
        verify(ticketRepository).save(overdue);

        ArgumentCaptor<TicketActivity> actCaptor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(actCaptor.capture());
        assertEquals(TicketActivityAction.SLA_BREACHED, actCaptor.getValue().action());
        assertEquals("system", actCaptor.getValue().actor());

        ArgumentCaptor<InboxEvent> eventCaptor = ArgumentCaptor.forClass(InboxEvent.class);
        verify(inboxEventPublisher).publish(eventCaptor.capture());
        assertEquals(InboxEvent.Kind.STATUS_CHANGED, eventCaptor.getValue().kind);
    }

    @Test
    void alreadyBreachedTicket_notReturned_noDoubleProcessing() {
        // findOverdue only returns tickets where sla_breached = false; simulating this
        when(ticketRepository.findOverdue(any())).thenReturn(Collections.emptyList());

        scheduler.checkOverdue();

        verifyNoInteractions(activityRepository, inboxEventPublisher);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void noOverdueTickets_noInteractions() {
        when(ticketRepository.findOverdue(any())).thenReturn(Collections.emptyList());

        scheduler.checkOverdue();

        verifyNoInteractions(activityRepository, inboxEventPublisher);
    }

    @Test
    void multipleOverdueTickets_allMarked() {
        Ticket t1 = makeOpenTicket("ext-1", false);
        Ticket t2 = makeOpenTicket("ext-2", false);
        t2.assignPersistedId(new TicketId("2"));

        when(ticketRepository.findOverdue(any())).thenReturn(Arrays.asList(t1, t2));
        when(ticketRepository.save(any())).thenReturn(t1.id());

        scheduler.checkOverdue();

        assertTrue(t1.slaBreached());
        assertTrue(t2.slaBreached());
        verify(ticketRepository, times(2)).save(any());
        verify(activityRepository, times(2)).save(any());
        verify(inboxEventPublisher, times(2)).publish(any());
    }
}
