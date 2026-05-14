package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * REST DTO for TagDefinition.
 */
public class TagDTO {
    public Long id;
    public String name;
    public String color;
    public String category;
    public Instant createdAt;
}
