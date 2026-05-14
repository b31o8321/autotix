package dev.autotix.domain.tag;

import java.time.Instant;

/**
 * Aggregate representing a named tag that can be applied to tickets.
 * The name is globally unique.
 */
public class TagDefinition {

    private Long id;
    private String name;
    private String color;       // hex string e.g. "#9BAAB8"
    private String category;    // nullable grouping label
    private Instant createdAt;

    private TagDefinition() {}

    public static TagDefinition create(String name, String color, String category) {
        TagDefinition t = new TagDefinition();
        t.name = name;
        t.color = color != null ? color : "#9BAAB8";
        t.category = category;
        t.createdAt = Instant.now();
        return t;
    }

    public static TagDefinition rehydrate(Long id, String name, String color,
                                          String category, Instant createdAt) {
        TagDefinition t = new TagDefinition();
        t.id = id;
        t.name = name;
        t.color = color;
        t.category = category;
        t.createdAt = createdAt;
        return t;
    }

    public void assignPersistedId(Long id) {
        this.id = id;
    }

    /** Update mutable fields (color and category). Name is immutable after creation. */
    public void updateColorAndCategory(String color, String category) {
        if (color != null) {
            this.color = color;
        }
        if (category != null) {
            this.category = category;
        }
    }

    public Long id() { return id; }
    public String name() { return name; }
    public String color() { return color; }
    public String category() { return category; }
    public Instant createdAt() { return createdAt; }
}
