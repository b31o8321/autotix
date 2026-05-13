package dev.autotix.interfaces.desk.dto;

import java.time.Instant;

/**
 * TODO: REST DTO for a single message.
 */
public class MessageDTO {
    public String direction;     // INBOUND / OUTBOUND
    public String author;
    public String content;
    public Instant occurredAt;
}
