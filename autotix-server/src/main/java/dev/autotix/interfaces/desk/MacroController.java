package dev.autotix.interfaces.desk;

import dev.autotix.application.macro.ListMacrosUseCase;
import dev.autotix.application.macro.RecordMacroUsageUseCase;
import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.infrastructure.auth.CurrentUser;
import dev.autotix.interfaces.admin.dto.MacroDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Desk REST endpoints for macros (any authenticated agent or admin).
 *
 * GET  /api/desk/macros         — list macros visible to current role
 * POST /api/desk/macros/{id}/use — record usage (fire-and-forget, returns 204)
 */
@RestController
@RequestMapping("/api/desk/macros")
public class MacroController {

    private final ListMacrosUseCase listMacros;
    private final RecordMacroUsageUseCase recordUsage;
    private final CurrentUser currentUser;

    public MacroController(ListMacrosUseCase listMacros,
                           RecordMacroUsageUseCase recordUsage,
                           CurrentUser currentUser) {
        this.listMacros = listMacros;
        this.recordUsage = recordUsage;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<MacroDTO> list() {
        return listMacros.listVisibleTo(currentUser.role()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<Void> use(@PathVariable Long id) {
        recordUsage.record(id);
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
}
