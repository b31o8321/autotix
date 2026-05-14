package dev.autotix.interfaces.desk.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * REST DTO for ticket (decoupled from domain).
 * Slice 8: added solvedAt, closedAt, parentTicketId, reopenCount.
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
    /** NEW / OPEN / WAITING_ON_CUSTOMER / WAITING_ON_INTERNAL / SOLVED / CLOSED / SPAM */
    public String status;
    public String assigneeId;
    public Set<String> tags;
    public List<MessageDTO> messages;     // optional, populated on detail GET
    public Instant createdAt;
    public Instant updatedAt;
    public Instant solvedAt;              // non-null when status == SOLVED
    public Instant closedAt;             // non-null when status == CLOSED
    public String parentTicketId;         // non-null when spawned from a prior ticket
    public int reopenCount;              // number of times reopened from SOLVED
    /** Slice 9: LOW / NORMAL / HIGH / URGENT */
    public String priority;
    /** Slice 9: QUESTION / INCIDENT / PROBLEM / TASK */
    public String type;
}
