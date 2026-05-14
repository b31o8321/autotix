package dev.autotix.domain.ticket;

import java.util.List;
import java.util.Optional;

/**
 * Port for attachment persistence.
 */
public interface AttachmentRepository {

    /** Persist and return the generated id. */
    Long save(Attachment attachment);

    Optional<Attachment> findById(Long id);

    List<Attachment> findByMessageId(Long messageId);

    List<Attachment> findByTicketId(TicketId ticketId);

    /** Set message_id on an existing orphan attachment. */
    void linkToMessage(Long attachmentId, Long messageId);

    void delete(Long id);
}
