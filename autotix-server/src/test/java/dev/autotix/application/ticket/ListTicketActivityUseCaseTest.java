package dev.autotix.application.ticket;

import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListTicketActivityUseCaseTest {

    @Mock private TicketActivityRepository activityRepository;
    private ListTicketActivityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListTicketActivityUseCase(activityRepository);
    }

    @Test
    void list_delegatesToRepository_withPagination() {
        TicketId ticketId = new TicketId("42");
        TicketActivity a1 = new TicketActivity(ticketId, "customer",
                TicketActivityAction.CREATED, Instant.now());
        TicketActivity a2 = new TicketActivity(ticketId, "agent:1",
                TicketActivityAction.REPLIED_PUBLIC, Instant.now());

        when(activityRepository.findByTicketId(eq(ticketId), eq(0), eq(50)))
                .thenReturn(Arrays.asList(a1, a2));

        List<TicketActivity> result = useCase.list(ticketId, 0, 50);

        assertEquals(2, result.size());
        verify(activityRepository).findByTicketId(ticketId, 0, 50);
    }

    @Test
    void list_emptyResult() {
        TicketId ticketId = new TicketId("99");
        when(activityRepository.findByTicketId(any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<TicketActivity> result = useCase.list(ticketId, 0, 10);
        assertTrue(result.isEmpty());
    }
}
