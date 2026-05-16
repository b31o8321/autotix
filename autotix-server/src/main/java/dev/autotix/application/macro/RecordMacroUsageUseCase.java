package dev.autotix.application.macro;

import dev.autotix.domain.macro.Macro;
import dev.autotix.domain.macro.MacroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Increment usage count for a macro. Best-effort — never throws to caller.
 */
@Service
public class RecordMacroUsageUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordMacroUsageUseCase.class);

    private final MacroRepository macroRepository;

    public RecordMacroUsageUseCase(MacroRepository macroRepository) {
        this.macroRepository = macroRepository;
    }

    public void record(Long macroId) {
        try {
            Optional<Macro> opt = macroRepository.findById(macroId);
            if (opt.isPresent()) {
                Macro macro = opt.get();
                macro.recordUsage();
                macroRepository.save(macro);
            }
        } catch (Exception e) {
            log.warn("Failed to record macro usage for id={}: {}", macroId, e.getMessage());
        }
    }
}
