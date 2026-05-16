package dev.autotix.domain.macro;

/**
 * Controls who can see / use a macro.
 */
public enum MacroAvailability {
    /** Visible only to admins in the management UI. */
    ADMIN_ONLY,
    /** Visible to all agents (and admins). This is the default. */
    AGENT,
    /** Visible to agents + may be exposed to the AI engine for context. */
    AI
}
