package dev.autotix.application.customfield;

import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldDefinitionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case: list custom field definitions filtered by appliesTo scope.
 */
@Service
public class ListCustomFieldsUseCase {

    private final CustomFieldDefinitionRepository repository;

    public ListCustomFieldsUseCase(CustomFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public List<CustomFieldDefinition> list(CustomFieldDefinition.AppliesTo appliesTo) {
        return repository.findAllByAppliesTo(appliesTo);
    }
}
