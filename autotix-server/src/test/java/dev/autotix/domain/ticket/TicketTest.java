package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for Ticket aggregate.
 * No Spring / DB involved.
 */
class TicketTest {

    private static final ChannelId CHANNEL = new ChannelId("channel-1");
    private static final String EXT_ID = "ext-001";

    private Message inboundMsg() {
        return new Message(MessageDirection.INBOUND, "customer", "Hello there", Instant.now());
    }

    private Message outboundMsg() {
        return new Message(MessageDirection.OUTBOUND, "ai", "Hello back", Instant.now());
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    @Test
    void openFromInbound_createsNewTicketWithFirstMessage() {
        Message first = inboundMsg();
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Subject", "customer@example.com", first);

        assertNotNull(ticket);
        // Initial status is NEW (not OPEN)
        assertEquals(TicketStatus.NEW, ticket.status());
        assertEquals(1, ticket.messages().size());
        assertEquals(MessageDirection.INBOUND, ticket.messages().get(0).direction());
        assertEquals(CHANNEL, ticket.channelId());
        assertEquals(EXT_ID, ticket.externalNativeId());
        assertNotNull(ticket.createdAt());
        assertEquals(0, ticket.reopenCount());
        assertNull(ticket.solvedAt());
        assertNull(ticket.closedAt());
        assertNull(ticket.parentTicketId());
    }

    // -----------------------------------------------------------------------
    // appendOutbound transitions
    // -----------------------------------------------------------------------

    @Test
    void appendOutbound_fromNew_movesToWaitingOnCustomer() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertEquals(TicketStatus.NEW, ticket.status());

        ticket.appendOutbound(outboundMsg());

        assertEquals(TicketStatus.WAITING_ON_CUSTOMER, ticket.status());
        assertEquals(2, ticket.messages().size());
    }

    @Test
    void appendOutbound_fromOpen_movesToWaitingOnCustomer() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        // Manually transition to OPEN via changeStatus (simulating a reopen)
        ticket.changeStatus(TicketStatus.OPEN);

        ticket.appendOutbound(outboundMsg());

        assertEquals(TicketStatus.WAITING_ON_CUSTOMER, ticket.status());
    }

    // -----------------------------------------------------------------------
    // appendInbound transitions
    // -----------------------------------------------------------------------

    @Test
    void appendInbound_fromWaitingOnCustomer_movesToOpen() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.appendOutbound(outboundMsg()); // NEW → WAITING_ON_CUSTOMER
        assertEquals(TicketStatus.WAITING_ON_CUSTOMER, ticket.status());

        ticket.appendInbound(inboundMsg());

        assertEquals(TicketStatus.OPEN, ticket.status());
        assertEquals(3, ticket.messages().size());
    }

    @Test
    void appendInbound_throwsOnClosed() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.permanentClose(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendInbound(inboundMsg()));
    }

    @Test
    void appendInbound_throwsOnSpam() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.markSpam(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendInbound(inboundMsg()));
    }

    @Test
    void appendInbound_throwsOnSolved() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.solve(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendInbound(inboundMsg()));
    }

    @Test
    void appendOutbound_throwsOnSolved() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.solve(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendOutbound(outboundMsg()));
    }

    // -----------------------------------------------------------------------
    // solve()
    // -----------------------------------------------------------------------

    @Test
    void solve_fromOpen_setsStatusAndSolvedAt() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.changeStatus(TicketStatus.OPEN);
        Instant now = Instant.now();

        ticket.solve(now);

        assertEquals(TicketStatus.SOLVED, ticket.status());
        assertEquals(now, ticket.solvedAt());
    }

    @Test
    void solve_fromWaitingOnCustomer_succeeds() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.appendOutbound(outboundMsg()); // → WAITING_ON_CUSTOMER

        ticket.solve(Instant.now());

        assertEquals(TicketStatus.SOLVED, ticket.status());
        assertNotNull(ticket.solvedAt());
    }

    @Test
    void solve_fromWaitingOnInternal_succeeds() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.changeStatus(TicketStatus.OPEN);
        ticket.escalateToInternal(Instant.now());

        ticket.solve(Instant.now());

        assertEquals(TicketStatus.SOLVED, ticket.status());
    }

    @Test
    void solve_fromClosed_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.permanentClose(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.solve(Instant.now()));
    }

    @Test
    void solve_fromSpam_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.markSpam(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.solve(Instant.now()));
    }

    // -----------------------------------------------------------------------
    // reopen()
    // -----------------------------------------------------------------------

    @Test
    void reopen_fromSolved_movesToOpenIncrementsCount() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        Instant solveTime = Instant.now();
        ticket.solve(solveTime);
        assertEquals(0, ticket.reopenCount());
        assertEquals(solveTime, ticket.solvedAt());

        Instant reopenTime = solveTime.plusSeconds(10);
        ticket.reopen(reopenTime);

        assertEquals(TicketStatus.OPEN, ticket.status());
        assertEquals(1, ticket.reopenCount());
        assertNull(ticket.solvedAt()); // cleared on reopen
    }

    @Test
    void reopen_twiceIncrementsCount() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        Instant now = Instant.now();
        ticket.solve(now);
        ticket.reopen(now.plusSeconds(1));
        ticket.appendInbound(inboundMsg()); // OPEN
        ticket.solve(now.plusSeconds(2));
        ticket.reopen(now.plusSeconds(3));

        assertEquals(2, ticket.reopenCount());
    }

    @Test
    void reopen_fromNonSolved_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.reopen(Instant.now()));
    }

    // -----------------------------------------------------------------------
    // isReopenable()
    // -----------------------------------------------------------------------

    @Test
    void isReopenable_withinWindow_returnsTrue() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        Instant solveTime = Instant.now().minusSeconds(60);
        ticket.solve(solveTime);

        Duration window = Duration.ofDays(7);
        assertTrue(ticket.isReopenable(Instant.now(), window));
    }

    @Test
    void isReopenable_pastWindow_returnsFalse() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        // solved 8 days ago
        Instant solveTime = Instant.now().minus(Duration.ofDays(8));
        ticket.solve(solveTime);

        Duration window = Duration.ofDays(7);
        assertFalse(ticket.isReopenable(Instant.now(), window));
    }

    @Test
    void isReopenable_notSolved_returnsFalse() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());

        assertFalse(ticket.isReopenable(Instant.now(), Duration.ofDays(7)));
    }

    // -----------------------------------------------------------------------
    // permanentClose()
    // -----------------------------------------------------------------------

    @Test
    void permanentClose_fromOpen_setsClosedAt() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.changeStatus(TicketStatus.OPEN);
        Instant now = Instant.now();

        ticket.permanentClose(now);

        assertEquals(TicketStatus.CLOSED, ticket.status());
        assertEquals(now, ticket.closedAt());
    }

    @Test
    void permanentClose_fromSolved_succeeds() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.solve(Instant.now());
        Instant closeTime = Instant.now();

        ticket.permanentClose(closeTime);

        assertEquals(TicketStatus.CLOSED, ticket.status());
        assertEquals(closeTime, ticket.closedAt());
    }

    @Test
    void permanentClose_onAlreadyClosed_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.permanentClose(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.permanentClose(Instant.now()));
    }

    @Test
    void permanentClose_fromSpam_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.markSpam(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.permanentClose(Instant.now()));
    }

    // -----------------------------------------------------------------------
    // markSpam()
    // -----------------------------------------------------------------------

    @Test
    void markSpam_fromAnyState_setsSpam() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.markSpam(Instant.now());

        assertEquals(TicketStatus.SPAM, ticket.status());
    }

    // -----------------------------------------------------------------------
    // changeStatus() guard
    // -----------------------------------------------------------------------

    @Test
    void changeStatus_onClosedTicket_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.permanentClose(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.changeStatus(TicketStatus.OPEN));
    }

    @Test
    void changeStatus_onSpamTicket_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.markSpam(Instant.now());

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.changeStatus(TicketStatus.OPEN));
    }

    // -----------------------------------------------------------------------
    // Tags
    // -----------------------------------------------------------------------

    @Test
    void addTags_isIdempotent() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());

        Set<String> tags = new HashSet<>();
        tags.add("billing");
        tags.add("urgent");

        ticket.addTags(tags);
        ticket.addTags(tags);

        assertEquals(2, ticket.tags().size());
        assertTrue(ticket.tags().contains("billing"));
        assertTrue(ticket.tags().contains("urgent"));
    }

    // -----------------------------------------------------------------------
    // assignTo — only sets assigneeId, does NOT change status
    // -----------------------------------------------------------------------

    @Test
    void assignTo_setsAssigneeId_doesNotChangeStatus() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        TicketStatus before = ticket.status(); // NEW

        ticket.assignTo("agent-42");

        // Status unchanged — assignment is orthogonal to status
        assertEquals(before, ticket.status());
        assertEquals("agent-42", ticket.assigneeId());
    }

    // -----------------------------------------------------------------------
    // Priority and Type
    // -----------------------------------------------------------------------

    @Test
    void defaultPriorityIsNormal() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertEquals(TicketPriority.NORMAL, ticket.priority());
    }

    @Test
    void defaultTypeIsQuestion() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertEquals(TicketType.QUESTION, ticket.type());
    }

    @Test
    void changePriority_updatesField() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.changePriority(TicketPriority.HIGH);
        assertEquals(TicketPriority.HIGH, ticket.priority());
    }

    @Test
    void changeType_updatesField() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.changeType(TicketType.INCIDENT);
        assertEquals(TicketType.INCIDENT, ticket.type());
    }

    @Test
    void changePriority_null_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.changePriority(null));
    }

    @Test
    void changeType_null_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.changeType(null));
    }

    // -----------------------------------------------------------------------
    // Internal notes
    // -----------------------------------------------------------------------

    @Test
    void appendInternalNote_doesNotChangeStatus() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        TicketStatus before = ticket.status();
        Message internal = new Message(MessageDirection.OUTBOUND, "agent:1",
                "Note for team", Instant.now(), MessageVisibility.INTERNAL);

        ticket.appendInternalNote(internal);

        assertEquals(before, ticket.status(), "Internal note must not change status");
        assertEquals(2, ticket.messages().size());
        assertEquals(MessageVisibility.INTERNAL, ticket.messages().get(1).visibility());
    }

    @Test
    void appendInternalNote_withPublicVisibility_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        Message publicMsg = new Message(MessageDirection.OUTBOUND, "agent:1",
                "Public", Instant.now(), MessageVisibility.PUBLIC);
        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendInternalNote(publicMsg));
    }

    @Test
    void appendInternalNote_onClosedTicket_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.permanentClose(Instant.now());
        Message internal = new Message(MessageDirection.OUTBOUND, "agent:1",
                "Note", Instant.now(), MessageVisibility.INTERNAL);
        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.appendInternalNote(internal));
    }

    // -----------------------------------------------------------------------
    // spawnFromClosed
    // -----------------------------------------------------------------------

    @Test
    void spawnFromClosed_setsParentTicketId() {
        Ticket parent = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        parent.assignPersistedId(new TicketId("99"));
        parent.permanentClose(Instant.now());

        Ticket child = Ticket.spawnFromClosed(
                CHANNEL, EXT_ID, "Re: Sub", "cust", inboundMsg(), parent.id());

        assertEquals(TicketStatus.NEW, child.status());
        assertNotNull(child.parentTicketId());
        assertEquals("99", child.parentTicketId().value());
    }
}
