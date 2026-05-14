package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * REST DTO for SLA policy (admin endpoints).
 */
public class SlaPolicyDTO {
    public String id;
    public String name;
    public String priority;         // TicketPriority enum name
    public int firstResponseMinutes;
    public int resolutionMinutes;
    public boolean enabled;
    public Instant createdAt;
    public Instant updatedAt;
}
