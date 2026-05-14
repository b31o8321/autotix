package dev.autotix.domain.event;

import dev.autotix.domain.channel.ChannelId;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standardized inbound event produced by a Platform Plugin's webhook parser.
 * This is the lingua franca that decouples platform-specific webhook payloads
 * from the Ticket Engine.
 *
 * Each Plugin must produce TicketEvent from raw webhook input.
 */
public final class TicketEvent {

    private final ChannelId channelId;
    private final EventType type;
    private final String externalTicketId;
    private final String customerIdentifier;
    private final String customerName;
    private final String subject;
    private final String messageBody;            // raw text/html as received
    private final Instant occurredAt;
    private final Map<String, Object> raw;       // original payload for audit/debug
    /** Slice 11: inbound attachments already uploaded to StorageProvider by the plugin. */
    private final List<InboundAttachment> attachments;

    /** Full constructor with attachments. */
    public TicketEvent(ChannelId channelId, EventType type, String externalTicketId,
                       String customerIdentifier, String customerName, String subject,
                       String messageBody, Instant occurredAt, Map<String, Object> raw,
                       List<InboundAttachment> attachments) {
        this.channelId = channelId;
        this.type = type;
        this.externalTicketId = externalTicketId;
        this.customerIdentifier = customerIdentifier;
        this.customerName = customerName;
        this.subject = subject;
        this.messageBody = messageBody;
        this.occurredAt = occurredAt;
        this.raw = raw;
        this.attachments = attachments != null ? attachments : Collections.<InboundAttachment>emptyList();
    }

    /** Backward-compatible constructor — no attachments. All existing callers use this. */
    public TicketEvent(ChannelId channelId, EventType type, String externalTicketId,
                       String customerIdentifier, String customerName, String subject,
                       String messageBody, Instant occurredAt, Map<String, Object> raw) {
        this(channelId, type, externalTicketId, customerIdentifier, customerName, subject,
                messageBody, occurredAt, raw, Collections.<InboundAttachment>emptyList());
    }

    public ChannelId channelId() { return channelId; }
    public EventType type() { return type; }
    public String externalTicketId() { return externalTicketId; }
    public String customerIdentifier() { return customerIdentifier; }
    public String customerName() { return customerName; }
    public String subject() { return subject; }
    public String messageBody() { return messageBody; }
    public Instant occurredAt() { return occurredAt; }
    public Map<String, Object> raw() { return raw; }
    public List<InboundAttachment> attachments() { return attachments; }

    // -----------------------------------------------------------------------

    /**
     * Attachment already uploaded to StorageProvider by the plugin before emitting the event.
     */
    public static final class InboundAttachment {
        private final String fileName;
        private final String contentType;
        private final long sizeBytes;
        private final String storageKey;
        private final String uploadedBy;

        public InboundAttachment(String fileName, String contentType, long sizeBytes,
                                 String storageKey, String uploadedBy) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.storageKey = storageKey;
            this.uploadedBy = uploadedBy;
        }

        public String fileName() { return fileName; }
        public String contentType() { return contentType; }
        public long sizeBytes() { return sizeBytes; }
        public String storageKey() { return storageKey; }
        public String uploadedBy() { return uploadedBy; }
    }
}
