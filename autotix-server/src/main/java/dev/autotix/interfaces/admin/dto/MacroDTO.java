package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * Data-transfer object for Macro. Used for both admin and desk endpoints.
 */
public class MacroDTO {

    public Long id;
    public String name;
    public String bodyMarkdown;
    public String category;
    public String availableTo;
    public int usageCount;
    public Instant createdAt;
    public Instant updatedAt;
}
