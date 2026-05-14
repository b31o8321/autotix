package dev.autotix.application.customfield;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import dev.autotix.domain.customfield.CustomFieldType;
import org.springframework.stereotype.Service;

/**
 * Use case: create a new custom field definition.
 * Throws ConflictException if a field with the same key already exists.
 */
@Service
public class CreateCustomFieldUseCase {

    private final CustomFieldDefinitionRepository repository;

    public CreateCustomFieldUseCase(CustomFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public CustomFieldDefinition create(String name, String key, CustomFieldType type,
                                        CustomFieldDefinition.AppliesTo appliesTo,
                                        boolean required, int displayOrder) {
        if (name == null || name.trim().isEmpty()) {
            throw new AutotixException.ValidationException("Custom field name must not be blank");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new AutotixException.ValidationException("Custom field key must not be blank");
        }
        if (repository.findByKey(key.trim()).isPresent()) {
            throw new AutotixException.ConflictException("Custom field with key '" + key.trim() + "' already exists");
        }
        CustomFieldDefinition field = CustomFieldDefinition.create(
                name.trim(), key.trim(), type, appliesTo, required, displayOrder);
        repository.save(field);
        return field;
    }
}
