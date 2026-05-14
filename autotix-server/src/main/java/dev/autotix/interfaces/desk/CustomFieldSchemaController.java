package dev.autotix.interfaces.desk;

import dev.autotix.application.customfield.ListCustomFieldsUseCase;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.interfaces.admin.dto.CustomFieldDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Desk endpoint for fetching custom field schema definitions.
 * Any authenticated agent can read — used when rendering ticket/customer forms.
 *
 * GET /api/desk/custom-fields?appliesTo=TICKET|CUSTOMER
 */
@RestController
@RequestMapping("/api/desk/custom-fields")
public class CustomFieldSchemaController {

    private final ListCustomFieldsUseCase listCustomFields;

    public CustomFieldSchemaController(ListCustomFieldsUseCase listCustomFields) {
        this.listCustomFields = listCustomFields;
    }

    @GetMapping
    public List<CustomFieldDTO> list(@RequestParam(required = false) String appliesTo) {
        CustomFieldDefinition.AppliesTo scope = parseAppliesTo(appliesTo);
        return listCustomFields.list(scope).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private CustomFieldDefinition.AppliesTo parseAppliesTo(String appliesTo) {
        if (appliesTo == null || appliesTo.trim().isEmpty()) {
            return CustomFieldDefinition.AppliesTo.TICKET;
        }
        try {
            return CustomFieldDefinition.AppliesTo.valueOf(appliesTo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid appliesTo value: " + appliesTo);
        }
    }

    private CustomFieldDTO toDTO(CustomFieldDefinition f) {
        CustomFieldDTO dto = new CustomFieldDTO();
        dto.id = f.id();
        dto.name = f.name();
        dto.key = f.key();
        dto.type = f.type().name();
        dto.appliesTo = f.appliesTo().name();
        dto.required = f.required();
        dto.displayOrder = f.displayOrder();
        dto.createdAt = f.createdAt();
        return dto;
    }
}
