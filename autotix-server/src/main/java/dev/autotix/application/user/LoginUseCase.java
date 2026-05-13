package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.infrastructure.auth.JwtTokenProvider;
import dev.autotix.infrastructure.auth.PasswordHasher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Validate email + password, issue JWT access + refresh tokens.
 *
 *  Flow:
 *    1. UserRepository.findByEmail; reject if missing or !enabled (uniform error — avoid enumeration)
 *    2. PasswordHasher.matches(raw, hash); reject if mismatch (same uniform error)
 *    3. user.recordLogin(now); save
 *    4. JwtTokenProvider.issue*(user); return TokenPair
 */
@Service
public class LoginUseCase {

    private static final String AUTH_FAILED = "Invalid credentials or account disabled";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenProvider tokenProvider;

    public LoginUseCase(UserRepository userRepository,
                        PasswordHasher passwordHasher,
                        JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenProvider = tokenProvider;
    }

    public TokenPair login(String email, String rawPassword) {
        Optional<User> found = userRepository.findByEmail(email);
        // Uniform rejection — never reveal whether email exists
        if (!found.isPresent() || !found.get().isEnabled()) {
            throw new AutotixException.AuthException(AUTH_FAILED);
        }
        User user = found.get();
        if (!passwordHasher.matches(rawPassword, user.passwordHash())) {
            throw new AutotixException.AuthException(AUTH_FAILED);
        }

        user.recordLogin(Instant.now());
        userRepository.save(user);

        String access = tokenProvider.issueAccess(user);
        String refresh = tokenProvider.issueRefresh(user);
        JwtTokenProvider.Claims accessClaims = tokenProvider.verify(access);
        JwtTokenProvider.Claims refreshClaims = tokenProvider.verify(refresh);
        return new TokenPair(access, refresh, accessClaims.expiresAt, refreshClaims.expiresAt);
    }

    /**
     * Verify a refresh token and issue a new access token (and rotate the refresh).
     */
    public TokenPair refresh(String refreshToken) {
        JwtTokenProvider.Claims claims;
        try {
            claims = tokenProvider.verify(refreshToken);
        } catch (AutotixException.AuthException ex) {
            throw new AutotixException.AuthException("Refresh token invalid or expired");
        }
        if (!"refresh".equals(claims.type)) {
            throw new AutotixException.AuthException("Token is not a refresh token");
        }
        Optional<User> found = userRepository.findById(
                new dev.autotix.domain.user.UserId(claims.userId));
        if (!found.isPresent() || !found.get().isEnabled()) {
            throw new AutotixException.AuthException("User not found or disabled");
        }
        User user = found.get();
        String newAccess = tokenProvider.issueAccess(user);
        String newRefresh = tokenProvider.issueRefresh(user);
        JwtTokenProvider.Claims ac = tokenProvider.verify(newAccess);
        JwtTokenProvider.Claims rc = tokenProvider.verify(newRefresh);
        return new TokenPair(newAccess, newRefresh, ac.expiresAt, rc.expiresAt);
    }

    /** Response value object (access + refresh token pair). */
    public static final class TokenPair {
        public final String accessToken;
        public final String refreshToken;
        public final long accessExpiresAt;    // unix millis
        public final long refreshExpiresAt;

        public TokenPair(String a, String r, long aExp, long rExp) {
            this.accessToken = a;
            this.refreshToken = r;
            this.accessExpiresAt = aExp;
            this.refreshExpiresAt = rExp;
        }
    }
}
