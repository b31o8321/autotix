package dev.autotix.domain.tag;

import java.util.List;
import java.util.Optional;

/**
 * Port for TagDefinition persistence.
 */
public interface TagDefinitionRepository {

    Long save(TagDefinition tag);

    Optional<TagDefinition> findByName(String name);

    List<TagDefinition> findAll();

    void delete(Long id);

    /**
     * Create or return existing TagDefinition by name.
     * If a tag with the given name already exists, updates its color and returns it.
     * If not found, creates a new one.
     *
     * @return the saved/existing TagDefinition
     */
    TagDefinition upsertByName(String name, String color);
}
