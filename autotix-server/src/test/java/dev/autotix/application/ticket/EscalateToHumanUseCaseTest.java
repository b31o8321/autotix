package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
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
class EscalateToHumanUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketActivityRepository activityRepository;

    private EscalateToHumanUseCase useCase;

    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new EscalateToHumanUseCase(ticketRepository, activityRepository);
        ticketId = new TicketId("55");
        Message msg = new Message(MessageDirection.INBOUND, "cust", "Help", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Subject", "cust", msg);
        ticket.assignPersistedId(ticketId);
    }

    @Test
    void escalate_callsEscalateToHuman_savesTicket_logsActivity() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.escalate(ticketId, "agent:1", "Customer wants human");

        verify(ticketRepository).save(any(Ticket.class));

        ArgumentCaptor<TicketActivity> activityCaptor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(activityCaptor.capture());

        TicketActivity activity = activityCaptor.getValue();
        assertEquals(TicketActivityAction.ESCALATED, activity.action());
        assertEquals("agent:1", activity.actor());
        assertNotNull(activity.details());
        assertTrue(activity.details().contains("Customer wants human"));
    }

    @Test
    void escalate_alreadyEscalated_throws() {
        ticket.escalateToHuman("agent:0", "first");
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.escalate(ticketId, "agent:1", "second"));

        verify(ticketRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }
}
