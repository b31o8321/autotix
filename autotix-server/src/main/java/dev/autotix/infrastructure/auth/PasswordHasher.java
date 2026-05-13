package dev.autotix.infrastructure.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt-based password hashing (default cost factor 10).
 */
@Component
public class PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * Hash the raw password.
     *
     * @param raw plain-text password (must not be null or empty)
     * @return BCrypt-encoded hash
     */
    public String hash(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("raw password must not be null or empty");
        }
        return encoder.encode(raw);
    }

    /**
     * Returns true when {@code raw} matches {@code hash}.
     */
    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return encoder.matches(raw, hash);
    }
}
