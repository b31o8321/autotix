package dev.autotix.domain.ticket;

import java.time.Instant;

/**
 * Value object representing a file attachment.
 * Pre-upload: id and messageId are null (orphan state).
 * After upload: id set by repository.save.
 * After reply linked: messageId set by repository.linkToMessage.
 */
public final class Attachment {

    private Long id;
    private Long messageId;
    private final TicketId ticketId;
    private final String key;
    private final String fileName;
    private final String contentType;
    private final long sizeBytes;
    private final String uploadedBy;
    private final Instant uploadedAt;

    public Attachment(Long id, Long messageId, TicketId ticketId,
                      String key, String fileName, String contentType,
                      long sizeBytes, String uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.messageId = messageId;
        this.ticketId = ticketId;
        this.key = key;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public Long id() { return id; }
    public Long messageId() { return messageId; }
    public TicketId ticketId() { return ticketId; }
    public String key() { return key; }
    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public String uploadedBy() { return uploadedBy; }
    public Instant uploadedAt() { return uploadedAt; }

    /** Set after repository.save. */
    public void setId(Long id) { this.id = id; }

    /** Set after repository.linkToMessage. */
    public void setMessageId(Long messageId) { this.messageId = messageId; }
}
