package dev.autotix.domain.customfield;

import java.time.Instant;

/**
 * Aggregate representing the schema definition for a custom field
 * that can be applied to Tickets or Customers.
 */
public class CustomFieldDefinition {

    /** What entity type this field applies to. */
    public enum AppliesTo {
        TICKET,
        CUSTOMER
    }

    private Long id;
    private String name;
    private String key;             // unique identifier used as map key
    private CustomFieldType type;
    private AppliesTo appliesTo;
    private boolean required;
    private int displayOrder;
    private Instant createdAt;

    private CustomFieldDefinition() {}

    public static CustomFieldDefinition create(String name, String key, CustomFieldType type,
                                               AppliesTo appliesTo, boolean required,
                                               int displayOrder) {
        CustomFieldDefinition f = new CustomFieldDefinition();
        f.name = name;
        f.key = key;
        f.type = type;
        f.appliesTo = appliesTo;
        f.required = required;
        f.displayOrder = displayOrder;
        f.createdAt = Instant.now();
        return f;
    }

    public static CustomFieldDefinition rehydrate(Long id, String name, String key,
                                                  CustomFieldType type, AppliesTo appliesTo,
                                                  boolean required, int displayOrder,
                                                  Instant createdAt) {
        CustomFieldDefinition f = new CustomFieldDefinition();
        f.id = id;
        f.name = name;
        f.key = key;
        f.type = type;
        f.appliesTo = appliesTo;
        f.required = required;
        f.displayOrder = displayOrder;
        f.createdAt = createdAt;
        return f;
    }

    public void assignPersistedId(Long id) {
        this.id = id;
    }

    public Long id() { return id; }
    public String name() { return name; }
    public String key() { return key; }
    public CustomFieldType type() { return type; }
    public AppliesTo appliesTo() { return appliesTo; }
    public boolean required() { return required; }
    public int displayOrder() { return displayOrder; }
    public Instant createdAt() { return createdAt; }
}
