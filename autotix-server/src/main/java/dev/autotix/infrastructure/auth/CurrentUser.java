package dev.autotix.infrastructure.auth;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper to read the currently authenticated user from the SecurityContext.
 * Controllers and use-cases call this instead of coupling to the Authentication API.
 */
@Component
public class CurrentUser {

    /**
     * Returns the UserId of the authenticated user.
     * The principal stored by JwtAuthenticationFilter is the userId string.
     */
    public UserId id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AutotixException.AuthException("No authenticated user in context");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof String) {
            return new UserId((String) principal);
        }
        throw new AutotixException.AuthException("Unexpected principal type: " + principal.getClass());
    }

    /**
     * Returns the role of the authenticated user, read from the JWT claims
     * stored as authentication details.
     */
    public UserRole role() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AutotixException.AuthException("No authenticated user in context");
        }
        Object details = auth.getDetails();
        if (details instanceof JwtTokenProvider.Claims) {
            String roleName = ((JwtTokenProvider.Claims) details).role;
            return UserRole.valueOf(roleName);
        }
        // Fallback: derive from first authority (ROLE_ADMIN → ADMIN)
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> {
                    String name = a.getAuthority();
                    if (name.startsWith("ROLE_")) {
                        name = name.substring(5);
                    }
                    return UserRole.valueOf(name);
                })
                .orElseThrow(() -> new AutotixException.AuthException("No role authority found"));
    }

    public boolean isAdmin() {
        return role() == UserRole.ADMIN;
    }
}
