package dev.autotix.application.ticket;

import dev.autotix.application.sla.ApplySlaPolicyUseCase;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketPriority;
import dev.autotix.domain.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeTicketPriorityUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketActivityRepository activityRepository;
    @Mock private ApplySlaPolicyUseCase applySlaPolicyUseCase;

    private ChangeTicketPriorityUseCase useCase;
    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new ChangeTicketPriorityUseCase(ticketRepository, activityRepository, applySlaPolicyUseCase);
        ticketId = new TicketId("1");
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Sub", "c",
                new Message(MessageDirection.INBOUND, "customer", "hi", Instant.now()));
        ticket.assignPersistedId(ticketId);
    }

    @Test
    void changePriority_updatesTicketAndLogsActivity() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        assertEquals(TicketPriority.NORMAL, ticket.priority());
        useCase.change(ticketId, TicketPriority.HIGH, "agent:1");

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals(TicketPriority.HIGH, ticketCaptor.getValue().priority());

        ArgumentCaptor<TicketActivity> actCaptor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(actCaptor.capture());
        TicketActivity activity = actCaptor.getValue();
        assertEquals(TicketActivityAction.PRIORITY_CHANGED, activity.action());
        assertEquals("agent:1", activity.actor());
        assertTrue(activity.details().contains("NORMAL"));
        assertTrue(activity.details().contains("HIGH"));
    }

    @Test
    void changePriority_ticketNotFound_throws() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());
        assertThrows(AutotixException.NotFoundException.class,
                () -> useCase.change(ticketId, TicketPriority.URGENT, "agent:1"));
    }
}
