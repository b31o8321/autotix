package dev.autotix.interfaces.desk.dto;

import java.time.Instant;

/**
 * REST DTO for a single ticket activity log entry.
 */
public class TicketActivityDTO {
    public Long id;
    public String ticketId;
    public String actor;
    public String action;
    public String details;
    public Instant occurredAt;
}
