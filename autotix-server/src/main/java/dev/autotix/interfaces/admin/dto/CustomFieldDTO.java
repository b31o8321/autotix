package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * REST DTO for CustomFieldDefinition.
 */
public class CustomFieldDTO {
    public Long id;
    public String name;
    public String key;
    public String type;       // TEXT | NUMBER | DATE
    public String appliesTo;  // TICKET | CUSTOMER
    public boolean required;
    public int displayOrder;
    public Instant createdAt;
}
