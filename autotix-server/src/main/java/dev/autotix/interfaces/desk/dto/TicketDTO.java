package dev.autotix.interfaces.desk.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * TODO: REST DTO for ticket (decoupled from domain).
 */
public class TicketDTO {
    public String id;
    public String channelId;
    public String platform;
    public String channelType;
    public String externalNativeId;
    public String subject;
    public String customerIdentifier;
    public String customerName;
    public String status;
    public String assigneeId;
    public Set<String> tags;
    public List<MessageDTO> messages;   // optional, populated on detail GET
    public Instant createdAt;
    public Instant updatedAt;
}
