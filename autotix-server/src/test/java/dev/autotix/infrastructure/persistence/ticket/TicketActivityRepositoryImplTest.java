package dev.autotix.infrastructure.persistence.ticket;

import dev.autotix.domain.channel.ChannelId;
import dev.autotix.domain.ticket.Message;
import dev.autotix.domain.ticket.MessageDirection;
import dev.autotix.domain.ticket.Ticket;
import dev.autotix.domain.ticket.TicketActivity;
import dev.autotix.domain.ticket.TicketActivityAction;
import dev.autotix.domain.ticket.TicketActivityRepository;
import dev.autotix.domain.ticket.TicketId;
import dev.autotix.domain.ticket.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TicketActivityRepositoryImpl against H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketActivityRepositoryImplTest {

    @Autowired
    private TicketActivityRepository activityRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private static final ChannelId TEST_CHANNEL = new ChannelId("ch-activity-test");

    private TicketId saveNewTicket() {
        String extId = "ext-" + UUID.randomUUID().toString().substring(0, 8);
        Message msg = new Message(MessageDirection.INBOUND, "customer", "Hello", Instant.now());
        Ticket ticket = Ticket.openFromInbound(TEST_CHANNEL, extId, "Activity test", "cust", msg);
        return ticketRepository.save(ticket);
    }

    @Test
    void save_assignsId() {
        TicketId ticketId = saveNewTicket();
        TicketActivity activity = new TicketActivity(
                ticketId, "customer", TicketActivityAction.CREATED, Instant.now());

        assertNull(activity.id());
        activityRepository.save(activity);
        assertNotNull(activity.id(), "id should be set after save");
    }

    @Test
    void findByTicketId_returnsInDescendingOrder() throws Exception {
        TicketId ticketId = saveNewTicket();

        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now().minusSeconds(5);
        Instant t3 = Instant.now();

        activityRepository.save(new TicketActivity(ticketId, "customer",
                TicketActivityAction.CREATED, t1));
        activityRepository.save(new TicketActivity(ticketId, "agent:1",
                TicketActivityAction.REPLIED_PUBLIC, null, t2));
        activityRepository.save(new TicketActivity(ticketId, "system",
                TicketActivityAction.SOLVED, t3));

        List<TicketActivity> results = activityRepository.findByTicketId(ticketId, 0, 100);

        assertEquals(3, results.size());
        // Should be desc order: t3, t2, t1
        assertTrue(results.get(0).occurredAt().compareTo(results.get(1).occurredAt()) >= 0,
                "First entry should be most recent");
        assertTrue(results.get(1).occurredAt().compareTo(results.get(2).occurredAt()) >= 0,
                "Second entry should be before first");
    }

    @Test
    void findByTicketId_pagination() {
        TicketId ticketId = saveNewTicket();

        for (int i = 0; i < 5; i++) {
            activityRepository.save(new TicketActivity(ticketId, "customer",
                    TicketActivityAction.CREATED, Instant.now().minusSeconds(100 - i)));
        }

        // Full list should have exactly 5 entries for this ticket
        List<TicketActivity> all = activityRepository.findByTicketId(ticketId, 0, 100);
        assertEquals(5, all.size(), "Should have exactly 5 entries for this ticket");

        // Limit=3 should return at most 3
        List<TicketActivity> page1 = activityRepository.findByTicketId(ticketId, 0, 3);
        assertTrue(page1.size() <= 3, "page1 should not exceed limit of 3");
    }

    @Test
    void findByTicketId_withDetails() {
        TicketId ticketId = saveNewTicket();
        String details = "{\"from\":\"NORMAL\",\"to\":\"HIGH\"}";
        activityRepository.save(new TicketActivity(ticketId, "agent:1",
                TicketActivityAction.PRIORITY_CHANGED, details, Instant.now()));

        List<TicketActivity> results = activityRepository.findByTicketId(ticketId, 0, 10);
        assertEquals(1, results.size());
        assertEquals(details, results.get(0).details());
        assertEquals(TicketActivityAction.PRIORITY_CHANGED, results.get(0).action());
    }
}
