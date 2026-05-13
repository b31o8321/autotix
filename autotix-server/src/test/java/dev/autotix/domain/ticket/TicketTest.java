package dev.autotix.domain.ticket;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.channel.ChannelId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void openFromInbound_createsOpenTicketWithFirstMessage() {
        Message first = inboundMsg();
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Subject", "customer@example.com", first);

        assertNotNull(ticket);
        assertEquals(TicketStatus.OPEN, ticket.status());
        assertEquals(1, ticket.messages().size());
        assertEquals(MessageDirection.INBOUND, ticket.messages().get(0).direction());
        assertEquals(CHANNEL, ticket.channelId());
        assertEquals(EXT_ID, ticket.externalNativeId());
        assertNotNull(ticket.createdAt());
    }

    @Test
    void appendOutbound_movesOpenToPending() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        assertEquals(TicketStatus.OPEN, ticket.status());

        ticket.appendOutbound(outboundMsg());

        assertEquals(TicketStatus.PENDING, ticket.status());
        assertEquals(2, ticket.messages().size());
        assertEquals(MessageDirection.OUTBOUND, ticket.messages().get(1).direction());
    }

    @Test
    void appendInbound_movesPendingBackToOpen() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.appendOutbound(outboundMsg());
        assertEquals(TicketStatus.PENDING, ticket.status());

        ticket.appendInbound(inboundMsg());

        assertEquals(TicketStatus.OPEN, ticket.status());
        assertEquals(3, ticket.messages().size());
    }

    @Test
    void close_onAlreadyClosed_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.close();
        assertEquals(TicketStatus.CLOSED, ticket.status());

        assertThrows(AutotixException.ValidationException.class, ticket::close);
    }

    @Test
    void changeStatus_onClosedTicket_throws() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());
        ticket.close();

        assertThrows(AutotixException.ValidationException.class,
                () -> ticket.changeStatus(TicketStatus.OPEN));
    }

    @Test
    void addTags_isIdempotent() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());

        Set<String> tags = new HashSet<>();
        tags.add("billing");
        tags.add("urgent");

        ticket.addTags(tags);
        ticket.addTags(tags); // add same tags again

        assertEquals(2, ticket.tags().size());
        assertTrue(ticket.tags().contains("billing"));
        assertTrue(ticket.tags().contains("urgent"));
    }

    @Test
    void assignTo_setsAssignedStatus() {
        Ticket ticket = Ticket.openFromInbound(CHANNEL, EXT_ID, "Sub", "cust", inboundMsg());

        ticket.assignTo("agent-42");

        assertEquals(TicketStatus.ASSIGNED, ticket.status());
        assertEquals("agent-42", ticket.assigneeId());
    }
}
