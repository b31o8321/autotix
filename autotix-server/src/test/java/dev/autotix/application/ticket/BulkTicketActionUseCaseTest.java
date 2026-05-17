package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.interfaces.desk.dto.BulkTicketActionRequest;
import dev.autotix.interfaces.desk.dto.BulkTicketActionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkTicketActionUseCaseTest {

    @Mock private SolveTicketUseCase solveTicket;
    @Mock private AssignTicketUseCase assignTicket;
    @Mock private MarkSpamUseCase markSpam;
    @Mock private UpdateTagsUseCase updateTags;
    @Mock private ChangeTicketStatusUseCase changeStatus;

    private BulkTicketActionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new BulkTicketActionUseCase(solveTicket, assignTicket, markSpam, updateTags, changeStatus);
    }

    // ── SOLVE ────────────────────────────────────────────────────────────────

    @Test
    void solve_threeTickets_allSucceed() {
        BulkTicketActionRequest req = buildRequest("SOLVE",
                Arrays.asList("t-1", "t-2", "t-3"), Collections.emptyMap());

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(3, resp.successCount);
        assertTrue(resp.failures.isEmpty());
        verify(solveTicket, times(3)).solve(any(TicketId.class), eq("agent:u-1"));
    }

    @Test
    void solve_oneAlreadySolved_partialSuccess() {
        doNothing().when(solveTicket).solve(eq(new TicketId("t-1")), any());
        doThrow(new AutotixException.ValidationException("ticket already SOLVED"))
                .when(solveTicket).solve(eq(new TicketId("t-2")), any());
        doNothing().when(solveTicket).solve(eq(new TicketId("t-3")), any());

        BulkTicketActionRequest req = buildRequest("SOLVE",
                Arrays.asList("t-1", "t-2", "t-3"), Collections.emptyMap());

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(2, resp.successCount);
        assertEquals(1, resp.failures.size());
        assertEquals("t-2", resp.failures.get(0).ticketId);
        assertTrue(resp.failures.get(0).reason.contains("already SOLVED"));
    }

    // ── ASSIGN ───────────────────────────────────────────────────────────────

    @Test
    void assign_invalidAssigneeId_allFail() {
        doThrow(new AutotixException.NotFoundException("Ticket not found: t-1"))
                .when(assignTicket).assign(eq(new TicketId("t-1")), eq("u-bad"));
        doThrow(new AutotixException.NotFoundException("Ticket not found: t-2"))
                .when(assignTicket).assign(eq(new TicketId("t-2")), eq("u-bad"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("assigneeId", "u-bad");
        BulkTicketActionRequest req = buildRequest("ASSIGN",
                Arrays.asList("t-1", "t-2"), payload);

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(0, resp.successCount);
        assertEquals(2, resp.failures.size());
    }

    @Test
    void assign_missingAssigneeId_throwsValidation() {
        BulkTicketActionRequest req = buildRequest("ASSIGN",
                Collections.singletonList("t-1"), Collections.emptyMap());

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(0, resp.successCount);
        assertEquals(1, resp.failures.size());
        assertTrue(resp.failures.get(0).reason.contains("assigneeId"));
    }

    // ── ADD_TAG ──────────────────────────────────────────────────────────────

    @Test
    void addTag_callsUpdateTagsOncePerTicket() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tag", "urgent");
        BulkTicketActionRequest req = buildRequest("ADD_TAG",
                Arrays.asList("t-1", "t-2", "t-3"), payload);

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(3, resp.successCount);
        verify(updateTags, times(3)).update(any(TicketId.class), argThat(s -> s.contains("urgent")), any());
    }

    // ── STATUS_CHANGE ────────────────────────────────────────────────────────

    @Test
    void statusChange_threeTickets_allSucceed() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "OPEN");
        BulkTicketActionRequest req = buildRequest("STATUS_CHANGE",
                Arrays.asList("t-1", "t-2", "t-3"), payload);

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(3, resp.successCount);
        verify(changeStatus, times(3)).change(any(TicketId.class),
                eq(dev.autotix.domain.ticket.TicketStatus.OPEN), eq("agent:u-1"));
    }

    // ── Empty IDs ────────────────────────────────────────────────────────────

    @Test
    void emptyTicketIds_returnsZeroZero() {
        BulkTicketActionRequest req = buildRequest("SOLVE", Collections.emptyList(), Collections.emptyMap());

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(0, resp.successCount);
        assertTrue(resp.failures.isEmpty());
        verifyNoInteractions(solveTicket);
    }

    @Test
    void nullTicketIds_returnsZeroZero() {
        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.action = "SOLVE";
        req.ticketIds = null;

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(0, resp.successCount);
        assertTrue(resp.failures.isEmpty());
    }

    // ── Unknown action ───────────────────────────────────────────────────────

    @Test
    void unknownAction_throwsValidationException() {
        BulkTicketActionRequest req = buildRequest("INVALID_ACTION",
                Collections.singletonList("t-1"), Collections.emptyMap());

        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.execute(req, "agent:u-1"));
    }

    // ── MARK_SPAM ────────────────────────────────────────────────────────────

    @Test
    void markSpam_threeTickets_allSucceed() {
        BulkTicketActionRequest req = buildRequest("MARK_SPAM",
                Arrays.asList("t-1", "t-2", "t-3"), Collections.emptyMap());

        BulkTicketActionResponse resp = useCase.execute(req, "agent:u-1");

        assertEquals(3, resp.successCount);
        verify(markSpam, times(3)).mark(any(TicketId.class), eq("agent:u-1"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private BulkTicketActionRequest buildRequest(String action, List<String> ticketIds,
                                                  Map<String, Object> payload) {
        BulkTicketActionRequest req = new BulkTicketActionRequest();
        req.action = action;
        req.ticketIds = ticketIds;
        req.payload = payload;
        return req;
    }
}
