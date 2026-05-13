package dev.autotix.domain.user;

/**
 * TODO: User role / permission level.
 *
 *  ADMIN   — full access: settings, channels, AI config, user management
 *  AGENT   — desk + inbox: handle tickets, reply, assign (within team), close
 *  VIEWER  — read-only: see tickets and reports, no mutation
 *
 *  Authorization rules (enforced in SecurityConfig + @PreAuthorize):
 *    /api/admin/**   -> ADMIN
 *    /api/desk/**    -> ADMIN, AGENT
 *    /api/inbox/**   -> ADMIN, AGENT
 *    /api/reports/** -> ADMIN, AGENT, VIEWER
 *    /v2/webhook/**  -> permitAll (signature-verified inside controller)
 *    /api/auth/**    -> permitAll
 */
public enum UserRole {
    ADMIN,
    AGENT,
    VIEWER
}
