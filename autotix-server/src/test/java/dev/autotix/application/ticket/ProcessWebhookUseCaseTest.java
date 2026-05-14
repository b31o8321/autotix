package dev.autotix.application.ticket;

import dev.autotix.application.customer.CustomerLookupService;
import dev.autotix.application.sla.ApplySlaPolicyUseCase;
import dev.autotix.domain.channel.Channel;
import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.channel.ChannelType;
import dev.autotix.domain.channel.PlatformType;
import dev.autotix.domain.event.EventType;
import dev.autotix.domain.event.TicketEvent;
import dev.autotix.domain.ticket.AttachmentRepository;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketDomainService;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketStatus;
import dev.autotix.application.automation.EvaluateRulesUseCase;
import dev.autotix.domain.ai.AIAction;
import dev.autotix.domain.customer.CustomerId;
import dev.autotix.infrastructure.inbox.InboxEventPublisher;
import dev.autotix.infrastructure.infra.idempotency.IdempotencyStore;
import dev.autotix.infrastructure.infra.queue.QueueProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessWebhookUseCaseTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private IdempotencyStore idempotencyStore;
    @Mock private QueueProvider queueProvider;
    @Mock private EvaluateRulesUseCase evaluateRules;
    @Mock private InboxEventPublisher inboxPublisher;
    @Mock private dev.autotix.domain.ticket.TicketActivityRepository activityRepository;
    @Mock private ApplySlaPolicyUseCase applySlaPolicyUseCase;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private CustomerLookupService customerLookupService;

    private TicketDomainService ticketDomainService;
    private ProcessWebhookUseCase useCase;

    private Channel channel;

    @BeforeEach
    void setUp() {
        ticketDomainService = new TicketDomainService();
        when(evaluateRules.evaluate(any(), any())).thenReturn(EvaluateRulesUseCase.RuleOutcome.noOp());
        when(customerLookupService.findOrCreate(any(), any(), any())).thenReturn(new CustomerId("100"));
        useCase = new ProcessWebhookUseCase(ticketRepository, idempotencyStore,
                queueProvider, ticketDomainService, evaluateRules, inboxPublisher, activityRepository,
                applySlaPolicyUseCase, attachmentRepository, customerLookupService);
        // @Value fields are not injected in plain Mockito tests — set the default explicitly
        ReflectionTestUtils.setField(useCase, "reopenWindowDays", 7);

        channel = Channel.rehydrate(
                new ChannelId("ch-1"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "Test Channel",
                "token123",
                null,
                true,
                true, // autoReplyEnabled
                Instant.now(),
                Instant.now());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private TicketEvent makeEvent(String externalId) {
        return new TicketEvent(
                new ChannelId("ch-1"),
                EventType.NEW_TICKET,
                externalId,
                "customer@example.com",
                "Alice",
                "Help me",
                "I need help",
                Instant.now(),
                Collections.emptyMap());
    }

    private Ticket makeExistingTicket(String externalId, TicketStatus status) {
        Message originalMsg = new Message(MessageDirection.INBOUND, "customer@example.com",
                "Original message", Instant.now().minusSeconds(60));
        Ticket t = Ticket.openFromInbound(
                new ChannelId("ch-1"), externalId, "Subject", "customer@example.com", originalMsg);
        t.assignPersistedId(new TicketId("99"));
        if (status == TicketStatus.OPEN) {
            t.changeStatus(TicketStatus.OPEN);
        } else if (status == TicketStatus.WAITING_ON_CUSTOMER) {
            t.appendOutbound(new Message(MessageDirection.OUTBOUND, "ai", "Hi", Instant.now().minusSeconds(30)));
        } else if (status == TicketStatus.SOLVED) {
            t.solve(Instant.now().minusSeconds(10));
        } else if (status == TicketStatus.CLOSED) {
            t.permanentClose(Instant.now().minusSeconds(10));
        } else if (status == TicketStatus.SPAM) {
            t.markSpam(Instant.now().minusSeconds(10));
        }
        return t;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void firstTime_createsTicket_andQueuesAi_whenAutoReplyEnabled() {
        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("1"));
            return new TicketId("1");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-ticket-1"));

        verify(ticketRepository).save(any(Ticket.class));
        verify(queueProvider).publish(eq("ai.dispatch"), eq("1"));
    }

    @Test
    void idempotentReplay_isNoOp() {
        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(false); // duplicate

        useCase.handle(channel, makeEvent("ext-ticket-2"));

        verifyNoInteractions(ticketRepository, queueProvider);
    }

    @Test
    void existingActiveTicket_appendsInbound_andSaves() {
        Ticket existingTicket = makeExistingTicket("ext-ticket-3", TicketStatus.OPEN);

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-ticket-3")))
                .thenReturn(Optional.of(existingTicket));
        when(ticketRepository.save(any())).thenReturn(new TicketId("99"));

        useCase.handle(channel, makeEvent("ext-ticket-3"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertEquals(2, saved.messages().size(), "Existing ticket should have 2 messages after append");
    }

    @Test
    void automationRule_skipsAi_doesNotEnqueue() {
        when(evaluateRules.evaluate(any(), any())).thenReturn(
                new EvaluateRulesUseCase.RuleOutcome(
                        java.util.Collections.emptyList(), null, true, AIAction.NONE));

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("3"));
            return new TicketId("3");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-ticket-skip-ai"));

        verify(ticketRepository).save(any(Ticket.class));
        verifyNoInteractions(queueProvider);
    }

    @Test
    void autoReplyDisabled_doesNotQueue() {
        Channel noAutoReply = Channel.rehydrate(
                new ChannelId("ch-2"),
                PlatformType.ZENDESK,
                ChannelType.EMAIL,
                "No-AI Channel",
                "token-noai",
                null,
                true,
                false,
                Instant.now(),
                Instant.now());

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("2"));
            return new TicketId("2");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(noAutoReply, makeEvent("ext-ticket-4"));

        verify(ticketRepository).save(any(Ticket.class));
        verifyNoInteractions(queueProvider);
    }

    // -----------------------------------------------------------------------
    // New spawn / reopen logic (Slice 8)
    // -----------------------------------------------------------------------

    @Test
    void inboundOnSolvedWithinWindow_reopensThenAppends() {
        // Solved only 10 seconds ago — well within 7-day window
        Ticket solved = makeExistingTicket("ext-solved-fresh", TicketStatus.SOLVED);
        // reopen window default = 7 days; 10-second-old solvedAt is within window

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-solved-fresh")))
                .thenReturn(Optional.of(solved));
        when(ticketRepository.save(any())).thenReturn(new TicketId("99"));

        useCase.handle(channel, makeEvent("ext-solved-fresh"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        // Same ticket (id=99), status reopened to OPEN, reopenCount incremented
        assertEquals(new TicketId("99"), saved.id());
        assertEquals(TicketStatus.OPEN, saved.status());
        assertEquals(1, saved.reopenCount());
        assertEquals(2, saved.messages().size()); // original + new inbound
    }

    @Test
    void inboundOnSolvedPastWindow_spawnsNewTicket() {
        // Set up a ticket solved 8 days ago (past 7-day window)
        Message originalMsg = new Message(MessageDirection.INBOUND, "customer@example.com",
                "Original", Instant.now().minusSeconds(60));
        Ticket oldSolved = Ticket.openFromInbound(
                new ChannelId("ch-1"), "ext-solved-old", "Subject", "customer@example.com", originalMsg);
        oldSolved.assignPersistedId(new TicketId("77"));
        // Solved 8 days ago
        oldSolved.solve(Instant.now().minus(Duration.ofDays(8)));

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-solved-old")))
                .thenReturn(Optional.of(oldSolved));
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("200"));
            return new TicketId("200");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-solved-old"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket spawned = captor.getValue();
        // A new ticket was created (id not 77), with parentTicketId = 77
        assertNotNull(spawned.parentTicketId());
        assertEquals("77", spawned.parentTicketId().value());
        assertEquals(TicketStatus.NEW, spawned.status());
        assertEquals(0, spawned.reopenCount());
    }

    @Test
    void inboundOnClosedTicket_spawnsNewTicketWithParentLink() {
        Ticket closed = makeExistingTicket("ext-closed", TicketStatus.CLOSED);

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-closed")))
                .thenReturn(Optional.of(closed));
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("101"));
            return new TicketId("101");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-closed"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket spawned = captor.getValue();
        assertNotNull(spawned.parentTicketId());
        assertEquals("99", spawned.parentTicketId().value()); // parent = old closed ticket
        assertEquals(TicketStatus.NEW, spawned.status());
    }

    @Test
    void inboundOnSpamTicket_spawnsNewTicket() {
        Ticket spam = makeExistingTicket("ext-spam", TicketStatus.SPAM);

        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), eq("ext-spam")))
                .thenReturn(Optional.of(spam));
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("102"));
            return new TicketId("102");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-spam"));

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket spawned = captor.getValue();
        assertNotNull(spawned.parentTicketId());
        assertEquals("99", spawned.parentTicketId().value());
        assertEquals(TicketStatus.NEW, spawned.status());
    }

    // -----------------------------------------------------------------------
    // Slice 12: CustomerLookupService integration
    // -----------------------------------------------------------------------

    @Test
    void newTicket_callsCustomerLookupService_andThreadsCustomerIdIntoTicket() {
        CustomerId expectedCustomerId = new CustomerId("777");
        when(customerLookupService.findOrCreate(any(), any(), any())).thenReturn(expectedCustomerId);
        when(idempotencyStore.tryMark(anyString(), any())).thenReturn(true);
        when(ticketRepository.findByChannelAndExternalId(any(), any())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.assignPersistedId(new TicketId("10"));
            return new TicketId("10");
        }).when(ticketRepository).save(any(Ticket.class));

        useCase.handle(channel, makeEvent("ext-customer-test"));

        // Verify CustomerLookupService was called
        verify(customerLookupService).findOrCreate(any(), any(), eq("customer@example.com"));

        // Verify the created ticket has the customerId set
        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(captor.capture());
        Ticket saved = captor.getValue();
        assertNotNull(saved.customerId());
        assertEquals("777", saved.customerId().value());
    }
}
