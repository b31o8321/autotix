package dev.autotix.application.ai;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.ai.AIReplyPort;
import dev.autotix.domain.ai.AIRequest;
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
import dev.autotix.infrastructure.ai.AIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateAIDraftUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private ChannelRepository channelRepository;
    @Mock private AIReplyPort aiReplyPort;

    private AIConfig aiConfig;
    private GenerateAIDraftUseCase useCase;

    private Channel channel;
    private Ticket ticket;
    private final TicketId ticketId = new TicketId("42");

    @BeforeEach
    void setUp() {
        aiConfig = new AIConfig();
        aiConfig.setModel("gpt-4o");

        useCase = new GenerateAIDraftUseCase(ticketRepository, channelRepository, aiReplyPort, aiConfig);

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

        Message inbound = new Message(MessageDirection.INBOUND, "alice@test.com",
                "Where is my order?", Instant.now());
        ticket = Ticket.openFromInbound(new ChannelId("ch-1"), "ext-1", "Order issue",
                "alice@test.com", inbound);
        ticket.assignPersistedId(ticketId);
    }

    @Test
    void happyPath_draftGeneratedWithModelName() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Your order is on the way!", AIAction.NONE, Collections.emptyList()));

        GenerateAIDraftUseCase.Draft draft = useCase.generate(ticketId, null);

        assertNotNull(draft);
        assertEquals("Your order is on the way!", draft.reply);
        assertEquals("gpt-4o", draft.modelName);
        assertEquals("NONE", draft.action);
        assertTrue(draft.latencyMs >= 0);
    }

    @Test
    void aiSuspended_throwsValidationException() {
        ticket.escalateToHuman("agent:1", "human needed");
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        AutotixException.ValidationException ex = assertThrows(
                AutotixException.ValidationException.class,
                () -> useCase.generate(ticketId, null));

        assertTrue(ex.getMessage().contains("suspended"));
        verifyNoInteractions(aiReplyPort);
    }

    @Test
    void styleHint_FRIENDLIER_appendsToSystemPromptOverride() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Warm reply!", AIAction.NONE, Collections.emptyList()));

        GenerateAIDraftUseCase.GenerationOptions opts = new GenerateAIDraftUseCase.GenerationOptions();
        opts.styleHint = GenerateAIDraftUseCase.StyleHint.FRIENDLIER;

        useCase.generate(ticketId, opts);

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiReplyPort).generate(captor.capture());
        assertEquals("Make your reply warm and reassuring.", captor.getValue().systemPromptOverride());
    }

    @Test
    void styleHint_FORMAL_appendsToSystemPromptOverride() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Formal reply.", AIAction.NONE, Collections.emptyList()));

        GenerateAIDraftUseCase.GenerationOptions opts = new GenerateAIDraftUseCase.GenerationOptions();
        opts.styleHint = GenerateAIDraftUseCase.StyleHint.FORMAL;

        useCase.generate(ticketId, opts);

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiReplyPort).generate(captor.capture());
        assertEquals("Use a formal professional tone.", captor.getValue().systemPromptOverride());
    }

    @Test
    void styleHint_SHORTER_appendsToSystemPromptOverride() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Short.", AIAction.NONE, Collections.emptyList()));

        GenerateAIDraftUseCase.GenerationOptions opts = new GenerateAIDraftUseCase.GenerationOptions();
        opts.styleHint = GenerateAIDraftUseCase.StyleHint.SHORTER;

        useCase.generate(ticketId, opts);

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiReplyPort).generate(captor.capture());
        assertEquals("Keep your reply under 60 words.", captor.getValue().systemPromptOverride());
    }

    @Test
    void styleHint_DEFAULT_hasNullSystemPromptOverride() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(channelRepository.findById(new ChannelId("ch-1"))).thenReturn(Optional.of(channel));
        when(aiReplyPort.generate(any())).thenReturn(
                new AIResponse("Default reply.", AIAction.NONE, Collections.emptyList()));

        GenerateAIDraftUseCase.GenerationOptions opts = new GenerateAIDraftUseCase.GenerationOptions();
        opts.styleHint = GenerateAIDraftUseCase.StyleHint.DEFAULT;

        useCase.generate(ticketId, opts);

        ArgumentCaptor<AIRequest> captor = ArgumentCaptor.forClass(AIRequest.class);
        verify(aiReplyPort).generate(captor.capture());
        assertNull(captor.getValue().systemPromptOverride());
    }
}
