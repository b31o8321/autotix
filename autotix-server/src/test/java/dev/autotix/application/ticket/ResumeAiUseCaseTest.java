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
class ResumeAiUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketActivityRepository activityRepository;

    private ResumeAiUseCase useCase;

    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        useCase = new ResumeAiUseCase(ticketRepository, activityRepository);
        ticketId = new TicketId("66");
        Message msg = new Message(MessageDirection.INBOUND, "cust", "Help", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-2", "Subject", "cust", msg);
        ticket.assignPersistedId(ticketId);
        // Pre-escalate so resumeAi is valid
        ticket.escalateToHuman("agent:1", "reason");
    }

    @Test
    void resume_whenSuspended_callsResumeAi_savesTicket_logsActivity() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.resume(ticketId, "agent:admin");

        verify(ticketRepository).save(any(Ticket.class));

        ArgumentCaptor<TicketActivity> activityCaptor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(activityCaptor.capture());

        TicketActivity activity = activityCaptor.getValue();
        assertEquals(TicketActivityAction.AI_RESUMED, activity.action());
        assertEquals("agent:admin", activity.actor());
    }

    @Test
    void resume_whenNotSuspended_throws() {
        // Ticket is not escalated
        Message msg2 = new Message(MessageDirection.INBOUND, "cust", "Hello", Instant.now());
        Ticket notSuspended = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-3", "Sub", "cust", msg2);
        notSuspended.assignPersistedId(new TicketId("67"));

        when(ticketRepository.findById(new TicketId("67"))).thenReturn(Optional.of(notSuspended));

        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.resume(new TicketId("67"), "agent:admin"));

        verify(ticketRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }
}
