package dev.autotix.application.sla;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.sla.SlaPolicy;
import dev.autotix.domain.sla.SlaPolicyRepository;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ApplySlaPolicyUseCase.
 */
@ExtendWith(MockitoExtension.class)
class ApplySlaPolicyUseCaseTest {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    private ApplySlaPolicyUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ApplySlaPolicyUseCase(slaPolicyRepository);
    }

    private Ticket newTicket(TicketPriority priority) {
        Ticket ticket = Ticket.openFromInbound(
                new ChannelId("ch-1"), "ext-1", "Subject", "customer",
                new Message(MessageDirection.INBOUND, "customer", "Hello", Instant.now()));
        ticket.changePriority(priority);
        return ticket;
    }

    @Test
    void apply_withPolicyInDb_usesDbValues() {
        SlaPolicy policy = SlaPolicy.create("High SLA", TicketPriority.HIGH, 60, 480, true);
        when(slaPolicyRepository.findByPriority(TicketPriority.HIGH)).thenReturn(Optional.of(policy));

        Ticket ticket = newTicket(TicketPriority.HIGH);
        Instant now = Instant.now();
        useCase.apply(ticket, now);

        assertNotNull(ticket.firstResponseDueAt());
        assertNotNull(ticket.resolutionDueAt());

        // Due timestamps anchored at createdAt + policy minutes
        long firstResponseMs = ticket.firstResponseDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();
        long resolutionMs = ticket.resolutionDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();

        // 60 minutes = 3600000ms (allow 100ms tolerance)
        assertTrue(Math.abs(firstResponseMs - 3600000L) < 1000,
                "Expected ~3600000ms but got " + firstResponseMs);
        // 480 minutes = 28800000ms
        assertTrue(Math.abs(resolutionMs - 28800000L) < 1000,
                "Expected ~28800000ms but got " + resolutionMs);
    }

    @Test
    void apply_withNoPolicyInDb_usesDefaults_forHigh() {
        when(slaPolicyRepository.findByPriority(TicketPriority.HIGH)).thenReturn(Optional.empty());

        Ticket ticket = newTicket(TicketPriority.HIGH);
        useCase.apply(ticket, Instant.now());

        assertNotNull(ticket.firstResponseDueAt());
        assertNotNull(ticket.resolutionDueAt());

        // Default HIGH: 60 min / 480 min
        long firstResponseMs = ticket.firstResponseDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();
        long resolutionMs = ticket.resolutionDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();
        assertTrue(Math.abs(firstResponseMs - 3600000L) < 1000);
        assertTrue(Math.abs(resolutionMs - 28800000L) < 1000);
    }

    @Test
    void apply_withNoPolicyInDb_usesDefaults_forUrgent() {
        when(slaPolicyRepository.findByPriority(TicketPriority.URGENT)).thenReturn(Optional.empty());

        Ticket ticket = newTicket(TicketPriority.URGENT);
        useCase.apply(ticket, Instant.now());

        // Default URGENT: 30 min / 240 min
        long firstResponseMs = ticket.firstResponseDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();
        long resolutionMs = ticket.resolutionDueAt().toEpochMilli() - ticket.createdAt().toEpochMilli();
        assertTrue(Math.abs(firstResponseMs - 1800000L) < 1000);   // 30 min = 1800s = 1800000ms
        assertTrue(Math.abs(resolutionMs - 14400000L) < 1000);     // 240 min = 14400s
    }

    @Test
    void apply_anchorsAtCreatedAt() {
        when(slaPolicyRepository.findByPriority(any())).thenReturn(Optional.empty());

        Ticket ticket = newTicket(TicketPriority.NORMAL);
        Instant before = ticket.createdAt();

        useCase.apply(ticket, Instant.now().plusSeconds(1000));

        // Due timestamps anchored at ticket.createdAt(), not "now"
        assertTrue(ticket.firstResponseDueAt().isAfter(before));
        long delta = ticket.firstResponseDueAt().toEpochMilli() - before.toEpochMilli();
        // NORMAL default: 240 min = 14400000ms
        assertTrue(Math.abs(delta - 14400000L) < 1000);
    }
}
