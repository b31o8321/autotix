package dev.autotix.application.macro;

import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroRepository;
import dev.autotix.domain.user.UserRole;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * List macros — all (admin) or filtered by role visibility (agent).
 */
@Service
public class ListMacrosUseCase {

    private final MacroRepository macroRepository;

    public ListMacrosUseCase(MacroRepository macroRepository) {
        this.macroRepository = macroRepository;
    }

    public List<Macro> listAll() {
        return macroRepository.findAll();
    }

    public List<Macro> listVisibleTo(UserRole role) {
        return macroRepository.findVisibleTo(role);
    }
}
