package dev.autotix.domain.macro;

import dev.autotix.domain.user.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Port for Macro persistence.
 */
public interface MacroRepository {

    Long save(Macro macro);

    Optional<Macro> findById(Long id);

    /** All macros ordered by usageCount DESC, then name ASC. */
    List<Macro> findAll();

    /**
     * Macros visible to the given role:
     * ADMIN → all macros.
     * AGENT → AGENT + AI macros (not ADMIN_ONLY).
     */
    List<Macro> findVisibleTo(UserRole role);

    void delete(Long id);
}
