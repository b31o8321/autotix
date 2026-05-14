package dev.autotix.application.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIResponse;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelRepository;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.event.InboxEvent;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.infra.lock.InMemoryLockProvider;
import dev.autotix.infrastructure.infra.lock.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchAIReplyUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private AIReplyPort aiReplyPort;
    @Mock private ReplyTicketUseCase replyTicketUseCase;
    @Mock private SolveTicketUseCase solveTicketUseCase;
    @Mock private InboxEventPublisher inboxPublisher;

    private LockProvider lockProvider;
    private DispatchAIReplyUseCase useCase;

    private Channel channel;
    private Ticket ticket;
    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        lockProvider = new InMemoryLockProvider();
        useCase = new DispatchAIReplyUseCase(
                ticketRepository, channelRepository, aiReplyPort,
                replyTicketUseCase, solveTicketUseCase, lockProvider, inboxPublisher);

        ticketId = new TicketId("10");
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
    }

    @Test
    void happyPath_callsReplyAndPublishesAiReplied() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Here is the answer", AIAction.NONE, Collections.emptyList()));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.dispatch(ticketId);

        verify(replyTicketUseCase).reply(eq(ticketId), eq("Here is the answer"), eq("ai"));
        verify(solveTicketUseCase, never()).solve(any());
        verify(inboxPublisher).publish(argThat(e -> e.kind == InboxEvent.Kind.AI_REPLIED));
    }

    @Test
    void aiThrows_escalatesToFallbackQueue() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenThrow(
                new AutotixException.IntegrationException("ai", "timeout"));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.dispatch(ticketId);

        // Ticket should be assigned to fallback queue (status stays NEW/OPEN, only assigneeId changes)
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        assertEquals("ai-fallback-queue", captor.getValue().assigneeId());

        // Reply should NOT have been sent
        verifyNoInteractions(replyTicketUseCase);
        verify(inboxPublisher).publish(argThat(e -> e.kind == InboxEvent.Kind.ASSIGNED));
    }

    @Test
    void lockContention_skipsDispatch() {
        // Hold the lock from another "thread"
        LockProvider.LockHandle held = lockProvider.tryAcquire(
                "ai-dispatch:10", Duration.ofMinutes(5));
        assertNotNull(held);

        useCase.dispatch(ticketId);

        // Nothing should happen — no ticket load, no AI call
        verifyNoInteractions(ticketRepository, aiReplyPort, replyTicketUseCase);

        held.close();
    }

    @Test
    void aiReturnsClose_solvesTicketAfterReply() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Solved!", AIAction.CLOSE, Collections.emptyList()));
        when(ticketRepository.save(any())).thenReturn(ticketId);

        useCase.dispatch(ticketId);

        verify(replyTicketUseCase).reply(eq(ticketId), eq("Solved!"), eq("ai"));
        // AI CLOSE now calls SolveTicketUseCase, NOT CloseTicketUseCase
        verify(solveTicketUseCase).solve(eq(ticketId));
    }
}
