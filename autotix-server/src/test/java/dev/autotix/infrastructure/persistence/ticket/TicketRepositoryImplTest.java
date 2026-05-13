package dev.autotix.infrastructure.persistence.ticket;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import dev.autotix.domain.ticket.TicketSearchQuery;
import dev.autotix.domain.ticket.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test against H2 in-memory DB.
 * Uses real MyBatis Plus + schema.sql.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketRepositoryImplTest {

    @Autowired
    private TicketRepository ticketRepository;

    private static final ChannelId TEST_CHANNEL = new ChannelId("ch-test-repo");

    private String uniqueExtId() {
        return "ext-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Message inboundMsg(String content) {
        return new Message(MessageDirection.INBOUND, "customer", content, Instant.now());
    }

    private Message outboundMsg(String content) {
        return new Message(MessageDirection.OUTBOUND, "ai", content, Instant.now());
    }

    @Test
    void saveAndFindById_roundTrip_preservesAllFields() {
        String extId = uniqueExtId();
        Ticket ticket = Ticket.openFromInbound(TEST_CHANNEL, extId, "Test Subject",
                "customer@example.com", inboundMsg("Hi there"));
        ticket.appendOutbound(outboundMsg("Hello back"));

        Set<String> tags = new HashSet<>();
        tags.add("billing");
        tags.add("urgent");
        ticket.addTags(tags);

        TicketId savedId = ticketRepository.save(ticket);
        assertNotNull(savedId);

        Optional<Ticket> loaded = ticketRepository.findById(savedId);
        assertTrue(loaded.isPresent());

        Ticket t = loaded.get();
        assertEquals(TicketStatus.PENDING, t.status()); // after outbound: OPEN -> PENDING
        assertEquals("Test Subject", t.subject());
        assertEquals("customer@example.com", t.customerIdentifier());
        assertEquals(TEST_CHANNEL, t.channelId());
        assertEquals(extId, t.externalNativeId());
        assertEquals(2, t.messages().size());
        assertEquals(MessageDirection.INBOUND, t.messages().get(0).direction());
        assertEquals(MessageDirection.OUTBOUND, t.messages().get(1).direction());
        assertEquals(2, t.tags().size());
        assertTrue(t.tags().contains("billing"));
        assertTrue(t.tags().contains("urgent"));
    }

    @Test
    void findByChannelAndExternalId_returnsCorrectTicket() {
        String extId = uniqueExtId();
        Ticket ticket = Ticket.openFromInbound(TEST_CHANNEL, extId, "Lookup Test",
                "lookup@example.com", inboundMsg("Find me"));
        ticketRepository.save(ticket);

        Optional<Ticket> found = ticketRepository.findByChannelAndExternalId(TEST_CHANNEL, extId);
        assertTrue(found.isPresent());
        assertEquals(extId, found.get().externalNativeId());
        assertEquals("Lookup Test", found.get().subject());
    }

    @Test
    void search_byStatus_returnsOnlyMatchingRows() {
        ChannelId searchChannel = new ChannelId("ch-search-" + UUID.randomUUID().toString().substring(0, 4));

        // Create an OPEN ticket
        String openExtId = uniqueExtId();
        Ticket openTicket = Ticket.openFromInbound(searchChannel, openExtId, "Open Ticket",
                "open@example.com", inboundMsg("open msg"));
        ticketRepository.save(openTicket);

        // Create a CLOSED ticket
        String closedExtId = uniqueExtId();
        Ticket closedTicket = Ticket.openFromInbound(searchChannel, closedExtId, "Closed Ticket",
                "closed@example.com", inboundMsg("closed msg"));
        closedTicket.close();
        ticketRepository.save(closedTicket);

        // Search for OPEN tickets in this channel
        TicketSearchQuery query = new TicketSearchQuery();
        query.status = TicketStatus.OPEN;
        query.channelId = searchChannel;
        query.limit = 20;

        List<Ticket> results = ticketRepository.search(query);

        assertTrue(results.size() >= 1);
        for (Ticket t : results) {
            assertEquals(TicketStatus.OPEN, t.status());
        }

        // Search for CLOSED tickets
        TicketSearchQuery closedQuery = new TicketSearchQuery();
        closedQuery.status = TicketStatus.CLOSED;
        closedQuery.channelId = searchChannel;
        closedQuery.limit = 20;

        List<Ticket> closedResults = ticketRepository.search(closedQuery);
        assertTrue(closedResults.size() >= 1);
        for (Ticket t : closedResults) {
            assertEquals(TicketStatus.CLOSED, t.status());
        }
    }

    @Test
    void save_existingTicket_updatesStatus() {
        String extId = uniqueExtId();
        Ticket ticket = Ticket.openFromInbound(TEST_CHANNEL, extId, "Update Test",
                "upd@example.com", inboundMsg("initial msg"));
        TicketId savedId = ticketRepository.save(ticket);

        // Reload, mutate, save again
        Optional<Ticket> loaded = ticketRepository.findById(savedId);
        assertTrue(loaded.isPresent());
        Ticket reloaded = loaded.get();
        reloaded.close();
        ticketRepository.save(reloaded);

        Optional<Ticket> updated = ticketRepository.findById(savedId);
        assertTrue(updated.isPresent());
        assertEquals(TicketStatus.CLOSED, updated.get().status());
    }
}
