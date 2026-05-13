package dev.autotix.interfaces.admin.dto;

import java.time.Instant;

/**
 * TODO: Admin REST DTO for User. Password hash NEVER exposed.
 */
public class UserDTO {
    public String id;
    public String email;
    public String displayName;
    public String role;            // ADMIN / AGENT / VIEWER
    public boolean enabled;
    public Instant lastLoginAt;
    public Instant createdAt;
}
