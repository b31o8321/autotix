package dev.autotix.application.macro;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroAvailability;
import dev.autotix.domain.macro.MacroRepository;
import org.springframework.stereotype.Service;

/**
 * Create a new macro. Throws ConflictException if name already exists.
 */
@Service
public class CreateMacroUseCase {

    private final MacroRepository macroRepository;

    public CreateMacroUseCase(MacroRepository macroRepository) {
        this.macroRepository = macroRepository;
    }

    public Macro create(String name, String bodyMarkdown, String category, MacroAvailability availableTo) {
        if (name == null || name.trim().isEmpty()) {
            throw new AutotixException.ValidationException("Macro name must not be blank");
        }
        if (bodyMarkdown == null || bodyMarkdown.trim().isEmpty()) {
            throw new AutotixException.ValidationException("Macro body must not be blank");
        }
        // Check uniqueness
        boolean duplicate = macroRepository.findAll().stream()
                .anyMatch(m -> m.name().equalsIgnoreCase(name.trim()));
        if (duplicate) {
            throw new AutotixException.ConflictException("Macro with name '" + name.trim() + "' already exists");
        }
        Macro macro = Macro.newMacro(name.trim(), bodyMarkdown, category,
                availableTo != null ? availableTo : MacroAvailability.AGENT);
        macroRepository.save(macro);
        return macro;
    }
}
