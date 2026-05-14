package dev.autotix.interfaces.admin;

import dev.autotix.application.customfield.CreateCustomFieldUseCase;
import dev.autotix.application.customfield.DeleteCustomFieldUseCase;
import dev.autotix.application.customfield.ListCustomFieldsUseCase;
import dev.autotix.application.customfield.UpdateCustomFieldUseCase;
import dev.autotix.domain.AutotixException;
import dev.autotix.domain.customfield.CustomFieldDefinition;
import dev.autotix.domain.customfield.CustomFieldType;
import dev.autotix.interfaces.admin.dto.CustomFieldDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for custom field definition management.
 *
 * GET    /api/admin/custom-fields?appliesTo=TICKET|CUSTOMER — list by scope
 * POST   /api/admin/custom-fields                           — create (409 if key conflict)
 * PUT    /api/admin/custom-fields/{id}                      — update name/required/displayOrder
 * DELETE /api/admin/custom-fields/{id}                      — delete
 */
@RestController
@RequestMapping("/api/admin/custom-fields")
@PreAuthorize("hasRole('ADMIN')")
public class CustomFieldAdminController {

    private final ListCustomFieldsUseCase listCustomFields;
    private final CreateCustomFieldUseCase createCustomField;
    private final UpdateCustomFieldUseCase updateCustomField;
    private final DeleteCustomFieldUseCase deleteCustomField;

    public CustomFieldAdminController(ListCustomFieldsUseCase listCustomFields,
                                      CreateCustomFieldUseCase createCustomField,
                                      UpdateCustomFieldUseCase updateCustomField,
                                      DeleteCustomFieldUseCase deleteCustomField) {
        this.listCustomFields = listCustomFields;
        this.createCustomField = createCustomField;
        this.updateCustomField = updateCustomField;
        this.deleteCustomField = deleteCustomField;
    }

    @GetMapping
    public List<CustomFieldDTO> list(@RequestParam(required = false) String appliesTo) {
        CustomFieldDefinition.AppliesTo scope = parseAppliesTo(appliesTo);
        return listCustomFields.list(scope).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public CustomFieldDTO create(@RequestBody CustomFieldDTO dto) {
        CustomFieldType type = parseType(dto.type);
        CustomFieldDefinition.AppliesTo appliesTo = parseAppliesTo(dto.appliesTo);
        CustomFieldDefinition field = createCustomField.create(
                dto.name, dto.key, type, appliesTo, dto.required, dto.displayOrder);
        return toDTO(field);
    }

    @PutMapping("/{id}")
    public CustomFieldDTO update(@PathVariable Long id, @RequestBody CustomFieldDTO dto) {
        CustomFieldDefinition field = updateCustomField.update(id, dto.name, dto.required, dto.displayOrder);
        return toDTO(field);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteCustomField.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CustomFieldDefinition.AppliesTo parseAppliesTo(String appliesTo) {
        if (appliesTo == null || appliesTo.trim().isEmpty()) {
            return CustomFieldDefinition.AppliesTo.TICKET; // default
        }
        try {
            return CustomFieldDefinition.AppliesTo.valueOf(appliesTo.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid appliesTo value: " + appliesTo);
        }
    }

    private CustomFieldType parseType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return CustomFieldType.TEXT; // default
        }
        try {
            return CustomFieldType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AutotixException.ValidationException("Invalid custom field type: " + type);
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
