package dev.autotix.interfaces.admin;

import dev.autotix.application.macro.CreateMacroUseCase;
import dev.autotix.application.macro.DeleteMacroUseCase;
import dev.autotix.application.macro.ListMacrosUseCase;
import dev.autotix.application.macro.UpdateMacroUseCase;
import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.interfaces.admin.dto.MacroDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin REST endpoints for macro management.
 *
 * GET    /api/admin/macros         — list all macros
 * POST   /api/admin/macros         — create macro (409 on duplicate name)
 * PUT    /api/admin/macros/{id}    — update macro
 * DELETE /api/admin/macros/{id}    — delete macro
 */
@RestController
@RequestMapping("/api/admin/macros")
@PreAuthorize("hasRole('ADMIN')")
public class MacroAdminController {

    private final ListMacrosUseCase listMacros;
    private final CreateMacroUseCase createMacro;
    private final UpdateMacroUseCase updateMacro;
    private final DeleteMacroUseCase deleteMacro;

    public MacroAdminController(ListMacrosUseCase listMacros,
                                CreateMacroUseCase createMacro,
                                UpdateMacroUseCase updateMacro,
                                DeleteMacroUseCase deleteMacro) {
        this.listMacros = listMacros;
        this.createMacro = createMacro;
        this.updateMacro = updateMacro;
        this.deleteMacro = deleteMacro;
    }

    @GetMapping
    public List<MacroDTO> list() {
        return listMacros.listAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public MacroDTO create(@RequestBody MacroDTO dto) {
        MacroAvailability availability = parseAvailability(dto.availableTo);
        Macro macro = createMacro.create(dto.name, dto.bodyMarkdown, dto.category, availability);
        return toDTO(macro);
    }

    @PutMapping("/{id}")
    public MacroDTO update(@PathVariable Long id, @RequestBody MacroDTO dto) {
        MacroAvailability availability = parseAvailability(dto.availableTo);
        Macro macro = updateMacro.update(id, dto.name, dto.bodyMarkdown, dto.category, availability);
        return toDTO(macro);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deleteMacro.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MacroDTO toDTO(Macro m) {
        MacroDTO dto = new MacroDTO();
        dto.id = m.id();
        dto.name = m.name();
        dto.bodyMarkdown = m.bodyMarkdown();
        dto.category = m.category();
        dto.availableTo = m.availableTo().name();
        dto.usageCount = m.usageCount();
        dto.createdAt = m.createdAt();
        dto.updatedAt = m.updatedAt();
        return dto;
    }

    private MacroAvailability parseAvailability(String value) {
        if (value == null) return MacroAvailability.AGENT;
        try {
            return MacroAvailability.valueOf(value);
        } catch (IllegalArgumentException e) {
            return MacroAvailability.AGENT;
        }
    }
}
