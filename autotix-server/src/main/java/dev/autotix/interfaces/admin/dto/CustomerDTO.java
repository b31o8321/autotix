package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * REST DTO for Customer (list view).
 */
public class CustomerDTO {
    public Long id;
    public String displayName;
    public String primaryEmail;
    public int identifierCount;
    public Instant createdAt;
}
