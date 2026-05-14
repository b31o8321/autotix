package dev.autotix.infrastructure.persistence.attachment;

import dev.autotix.domain.ticket.Attachment;
import dev.autotix.domain.ticket.AttachmentRepository;
import dev.autotix.domain.ticket.TicketId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for AttachmentRepositoryImpl using H2 in-memory DB.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttachmentRepositoryImplTest {

    @Autowired
    AttachmentRepository attachmentRepository;

    private Attachment buildAttachment(String keySuffix) {
        return new Attachment(
                null, null,
                new TicketId("999"),
                "attachments/2025/05/999/" + keySuffix,
                keySuffix + ".txt",
                "text/plain",
                1024L,
                "agent",
                Instant.now());
    }

    @Test
    void saveAndFindById_roundTrip() {
        Attachment att = buildAttachment("roundtrip-" + System.nanoTime());
        Long id = attachmentRepository.save(att);

        assertNotNull(id);
        assertTrue(id > 0);

        Optional<Attachment> found = attachmentRepository.findById(id);
        assertTrue(found.isPresent());
        assertEquals(att.fileName(), found.get().fileName());
        assertEquals(att.key(), found.get().key());
        assertEquals("text/plain", found.get().contentType());
        assertEquals(1024L, found.get().sizeBytes());
        assertEquals("agent", found.get().uploadedBy());
        assertEquals(new TicketId("999").value(), found.get().ticketId().value());
    }

    @Test
    void linkToMessage_updatesMessageId() {
        Attachment att = buildAttachment("link-" + System.nanoTime());
        Long id = attachmentRepository.save(att);

        // Initially messageId should be null
        Optional<Attachment> before = attachmentRepository.findById(id);
        assertNull(before.get().messageId());

        attachmentRepository.linkToMessage(id, 42L);

        Optional<Attachment> after = attachmentRepository.findById(id);
        assertEquals(42L, after.get().messageId());
    }

    @Test
    void findByMessageId_returnsLinkedRowsOnly() {
        long msgId = System.nanoTime();

        Attachment att1 = buildAttachment("msg-linked-a-" + System.nanoTime());
        Long id1 = attachmentRepository.save(att1);
        attachmentRepository.linkToMessage(id1, msgId);

        Attachment att2 = buildAttachment("msg-linked-b-" + System.nanoTime());
        Long id2 = attachmentRepository.save(att2);
        attachmentRepository.linkToMessage(id2, msgId);

        Attachment unlinked = buildAttachment("msg-unlinked-" + System.nanoTime());
        attachmentRepository.save(unlinked);

        List<Attachment> results = attachmentRepository.findByMessageId(msgId);
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(a -> a.id().equals(id1)));
        assertTrue(results.stream().anyMatch(a -> a.id().equals(id2)));
    }

    @Test
    void findByTicketId_returnsAttachmentsForTicket() {
        String uniqueTicketId = String.valueOf(System.nanoTime()).substring(0, 9);
        TicketId tid = new TicketId(uniqueTicketId);

        Attachment att = new Attachment(
                null, null, tid,
                "attachments/2025/05/" + uniqueTicketId + "/test-" + System.nanoTime() + ".txt",
                "test.txt", "text/plain", 100L, "agent", Instant.now());
        attachmentRepository.save(att);

        List<Attachment> results = attachmentRepository.findByTicketId(tid);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(a -> a.ticketId().value().equals(uniqueTicketId)));
    }
}
