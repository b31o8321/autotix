package dev.autotix.application.customfield;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import org.springframework.stereotype.Service;

/**
 * Use case: delete a custom field definition by id.
 */
@Service
public class DeleteCustomFieldUseCase {

    private final CustomFieldDefinitionRepository repository;

    public DeleteCustomFieldUseCase(CustomFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public void delete(Long id) {
        repository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Custom field not found: " + id));
        repository.delete(id);
    }
}
