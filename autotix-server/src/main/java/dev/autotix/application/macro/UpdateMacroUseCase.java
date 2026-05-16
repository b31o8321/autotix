package dev.autotix.application.macro;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.domain.macro.MacroRepository;
import org.springframework.stereotype.Service;

/**
 * Update an existing macro's fields.
 */
@Service
public class UpdateMacroUseCase {

    private final MacroRepository macroRepository;

    public UpdateMacroUseCase(MacroRepository macroRepository) {
        this.macroRepository = macroRepository;
    }

    public Macro update(Long id, String name, String bodyMarkdown,
                        String category, MacroAvailability availableTo) {
        Macro macro = macroRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Macro not found: " + id));

        if (name != null && !name.trim().isEmpty() && !name.trim().equalsIgnoreCase(macro.name())) {
            // Check uniqueness of new name
            boolean duplicate = macroRepository.findAll().stream()
                    .filter(m -> !m.id().equals(id))
                    .anyMatch(m -> m.name().equalsIgnoreCase(name.trim()));
            if (duplicate) {
                throw new AutotixException.ConflictException("Macro with name '" + name.trim() + "' already exists");
            }
            macro.rename(name.trim());
        }
        if (bodyMarkdown != null) {
            macro.updateBody(bodyMarkdown);
        }
        if (category != null || bodyMarkdown != null) {
            macro.updateCategory(category);
        }
        if (availableTo != null) {
            macro.updateAvailability(availableTo);
        }
        macroRepository.save(macro);
        return macro;
    }
}
