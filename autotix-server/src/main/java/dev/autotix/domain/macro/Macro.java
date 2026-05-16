package dev.autotix.domain.macro;

import java.time.Instant;

/**
 * Aggregate: a saved-reply template (macro) that agents can insert into replies.
 * Names are unique per workspace.
 */
public class Macro {

    private Long id;
    private String name;
    private String bodyMarkdown;
    private String category;
    private MacroAvailability availableTo;
    private int usageCount;
    private Instant createdAt;
    private Instant updatedAt;

    private Macro() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    public static Macro newMacro(String name, String bodyMarkdown,
                                 String category, MacroAvailability availableTo) {
        Macro m = new Macro();
        m.name = name;
        m.bodyMarkdown = bodyMarkdown;
        m.category = category;
        m.availableTo = availableTo != null ? availableTo : MacroAvailability.AGENT;
        m.usageCount = 0;
        Instant now = Instant.now();
        m.createdAt = now;
        m.updatedAt = now;
        return m;
    }

    public static Macro rehydrate(Long id, String name, String bodyMarkdown,
                                  String category, MacroAvailability availableTo,
                                  int usageCount, Instant createdAt, Instant updatedAt) {
        Macro m = new Macro();
        m.id = id;
        m.name = name;
        m.bodyMarkdown = bodyMarkdown;
        m.category = category;
        m.availableTo = availableTo;
        m.usageCount = usageCount;
        m.createdAt = createdAt;
        m.updatedAt = updatedAt;
        return m;
    }

    // -----------------------------------------------------------------------
    // Domain methods
    // -----------------------------------------------------------------------

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = Instant.now();
    }

    public void updateBody(String newBody) {
        this.bodyMarkdown = newBody;
        this.updatedAt = Instant.now();
    }

    public void updateCategory(String newCategory) {
        this.category = newCategory;
        this.updatedAt = Instant.now();
    }

    public void updateAvailability(MacroAvailability availability) {
        this.availableTo = availability;
        this.updatedAt = Instant.now();
    }

    public void recordUsage() {
        this.usageCount++;
        this.updatedAt = Instant.now();
    }

    /** Called by persistence layer after INSERT. */
    public void assignPersistedId(Long id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public Long id() { return id; }
    public String name() { return name; }
    public String bodyMarkdown() { return bodyMarkdown; }
    public String category() { return category; }
    public MacroAvailability availableTo() { return availableTo; }
    public int usageCount() { return usageCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
