package dev.autotix.domain.customfield;

import java.util.List;
import java.util.Optional;

/**
 * Port for CustomFieldDefinition persistence.
 */
public interface CustomFieldDefinitionRepository {

    Long save(CustomFieldDefinition field);

    Optional<CustomFieldDefinition> findByKey(String key);

    /**
     * Return all field definitions for the given entity type, ordered by displayOrder ascending.
     */
    List<CustomFieldDefinition> findAllByAppliesTo(CustomFieldDefinition.AppliesTo appliesTo);

    void delete(Long id);
}
