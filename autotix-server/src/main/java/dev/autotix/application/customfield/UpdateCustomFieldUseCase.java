package dev.autotix.application.customfield;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case: update mutable fields of a custom field definition.
 * Type and key are immutable after creation.
 */
@Service
public class UpdateCustomFieldUseCase {

    private final CustomFieldDefinitionRepository repository;

    public UpdateCustomFieldUseCase(CustomFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public CustomFieldDefinition update(Long id, String name, boolean required, int displayOrder) {
        CustomFieldDefinition field = repository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Custom field not found: " + id));
        field.updateMutableFields(name, required, displayOrder);
        repository.save(field);
        return field;
    }
}
