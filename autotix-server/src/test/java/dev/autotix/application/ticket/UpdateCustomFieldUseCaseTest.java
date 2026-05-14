package dev.autotix.application.ticket;

import dev.autotix.application.ticket.UpdateTicketCustomFieldUseCase;
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
class UpdateCustomFieldUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketActivityRepository activityRepository;

    private UpdateTicketCustomFieldUseCase useCase;

    private Ticket ticket;
    private final TicketId ticketId = new TicketId("20");

    @BeforeEach
    void setUp() {
        useCase = new UpdateTicketCustomFieldUseCase(ticketRepository, activityRepository);

        Message inbound = new Message(MessageDirection.INBOUND, "carol@test.com",
                "Hello", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Subject",
                "carol@test.com", inbound);
        ticket.assignPersistedId(ticketId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any())).thenReturn(ticketId);
    }

    @Test
    void setValue_callsSetCustomField() {
        useCase.update(ticketId, "order_number", "1234");

        assertEquals("1234", ticket.customFields().get("order_number"));
    }

    @Test
    void clearValue_callsClearCustomField() {
        ticket.setCustomField("order_number", "1234");

        useCase.update(ticketId, "order_number", null);

        assertFalse(ticket.customFields().containsKey("order_number"));
    }

    @Test
    void emptyValue_callsClearCustomField() {
        ticket.setCustomField("plan", "pro");

        useCase.update(ticketId, "plan", "  ");

        assertFalse(ticket.customFields().containsKey("plan"));
    }

    @Test
    void customFieldChanged_activityLogged() {
        useCase.update(ticketId, "region", "EU");

        ArgumentCaptor<TicketActivity> captor = ArgumentCaptor.forClass(TicketActivity.class);
        verify(activityRepository).save(captor.capture());

        TicketActivity activity = captor.getValue();
        assertEquals(ticketId, activity.ticketId());
        assertEquals(TicketActivityAction.CUSTOM_FIELD_CHANGED, activity.action());
        assertNotNull(activity.details());
        assertTrue(activity.details().contains("region"));
        assertTrue(activity.details().contains("EU"));
    }
}
