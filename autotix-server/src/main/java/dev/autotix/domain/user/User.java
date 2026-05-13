package dev.autotix.domain.user;

import dev.autotix.domain.AutotixException;

import java.time.Instant;

/**
 * Aggregate root for application user (admin / agent / viewer).
 *  - email is the login identifier (unique, stored lower-cased)
 *  - passwordHash is BCrypt-encoded; raw password never stored
 *  - role can be changed by an ADMIN; an admin cannot demote the LAST admin (invariant at UseCase level)
 */
public class User {

    private UserId id;
    private String email;
    private String displayName;
    private String passwordHash;
    private UserRole role;
    private boolean enabled;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;

    /** Private constructor — use factory methods or rehydration. */
    private User() {}

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Creates a new enabled User with the given role.
     * Password must already be hashed by the caller (via PasswordHasher).
     */
    public static User register(String email, String displayName, String passwordHash, UserRole role) {
        if (email == null || email.trim().isEmpty()) {
            throw new AutotixException.ValidationException("email must not be blank");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new AutotixException.ValidationException("displayName must not be blank");
        }
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new AutotixException.ValidationException("passwordHash must not be blank");
        }
        if (role == null) {
            throw new AutotixException.ValidationException("role must not be null");
        }
        Instant now = Instant.now();
        User u = new User();
        u.email = email.toLowerCase().trim();
        u.displayName = displayName.trim();
        u.passwordHash = passwordHash;
        u.role = role;
        u.enabled = true;
        u.createdAt = now;
        u.updatedAt = now;
        return u;
    }

    // -----------------------------------------------------------------------
    // Rehydration (called by repository impl)
    // -----------------------------------------------------------------------

    public static User rehydrate(UserId id, String email, String displayName, String passwordHash,
                                 UserRole role, boolean enabled, Instant lastLoginAt,
                                 Instant createdAt, Instant updatedAt) {
        User u = new User();
        u.id = id;
        u.email = email;
        u.displayName = displayName;
        u.passwordHash = passwordHash;
        u.role = role;
        u.enabled = enabled;
        u.lastLoginAt = lastLoginAt;
        u.createdAt = createdAt;
        u.updatedAt = updatedAt;
        return u;
    }

    // -----------------------------------------------------------------------
    // Domain behaviors
    // -----------------------------------------------------------------------

    /** Change password; caller must pass pre-hashed value. */
    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.trim().isEmpty()) {
            throw new AutotixException.ValidationException("newPasswordHash must not be blank");
        }
        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
    }

    /** Change role; caller (UseCase) must enforce "cannot demote last admin" before calling. */
    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new AutotixException.ValidationException("newRole must not be null");
        }
        this.role = newRole;
        this.updatedAt = Instant.now();
    }

    /** Soft-delete the user (sets enabled=false). */
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    /** Stamp lastLoginAt on successful login. */
    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
        this.updatedAt = now;
    }

    // -----------------------------------------------------------------------
    // Public setter for id — called by repository after INSERT to inject the
    // generated PK back into the domain object.
    // -----------------------------------------------------------------------

    public void setId(UserId id) {
        this.id = id;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public UserId id() { return id; }
    public String email() { return email; }
    public String displayName() { return displayName; }
    public String passwordHash() { return passwordHash; }
    public UserRole role() { return role; }
    public boolean isEnabled() { return enabled; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
