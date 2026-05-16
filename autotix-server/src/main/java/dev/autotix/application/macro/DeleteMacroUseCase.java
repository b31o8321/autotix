package dev.autotix.application.macro;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.macro.MacroRepository;
import org.springframework.stereotype.Service;

/**
 * Delete a macro by id.
 */
@Service
public class DeleteMacroUseCase {

    private final MacroRepository macroRepository;

    public DeleteMacroUseCase(MacroRepository macroRepository) {
        this.macroRepository = macroRepository;
    }

    public void delete(Long id) {
        macroRepository.findById(id)
                .orElseThrow(() -> new AutotixException.NotFoundException("Macro not found: " + id));
        macroRepository.delete(id);
    }
}
