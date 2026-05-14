package dev.autotix.interfaces.desk.dto;

import java.time.Instant;
import java.util.List;

/**
 * REST DTO for a single message.
 */
public class MessageDTO {
    public String direction;     // INBOUND / OUTBOUND
    public String author;
    public String content;
    public Instant occurredAt;
    /** Slice 9: PUBLIC (default) or INTERNAL */
    public String visibility;
    /** Slice 11: file attachments linked to this message */
    public List<AttachmentDTO> attachments;
}
