package dev.autotix.infrastructure.auth;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Issue + verify JWT tokens (jjwt 0.11.x).
 * Claims:
 *   - sub:   userId
 *   - email: user email
 *   - role:  ADMIN / AGENT / VIEWER
 *   - typ:   "access" or "refresh"
 * Signing: HS256 with AuthConfig.jwtSecret (min 32 chars enforced at startup).
 */
@Component
public class JwtTokenProvider {

    private final AuthConfig config;
    private Key signingKey;

    public JwtTokenProvider(AuthConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        String secret = config.getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "autotix.auth.jwt-secret must be at least 32 characters long. " +
                    "Current length: " + (secret == null ? 0 : secret.length()));
        }
        signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String issueAccess(User user) {
        long nowMs = System.currentTimeMillis();
        long expiryMs = nowMs + TimeUnit.MINUTES.toMillis(config.getAccessTtlMinutes());
        return Jwts.builder()
                .setSubject(user.id().value())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("typ", "access")
                .setIssuedAt(new Date(nowMs))
                .setExpiration(new Date(expiryMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String issueRefresh(User user) {
        long nowMs = System.currentTimeMillis();
        long expiryMs = nowMs + TimeUnit.DAYS.toMillis(config.getRefreshTtlDays());
        return Jwts.builder()
                .setSubject(user.id().value())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .claim("typ", "refresh")
                .setIssuedAt(new Date(nowMs))
                .setExpiration(new Date(expiryMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parse and validate the token. Throws AuthException on any failure.
     */
    public Claims verify(String token) {
        try {
            io.jsonwebtoken.Claims body = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Claims c = new Claims();
            c.userId = body.getSubject();
            c.email = (String) body.get("email");
            c.role = (String) body.get("role");
            c.type = (String) body.get("typ");
            c.expiresAt = body.getExpiration().getTime();
            return c;
        } catch (ExpiredJwtException ex) {
            throw new AutotixException.AuthException("Token has expired");
        } catch (JwtException ex) {
            throw new AutotixException.AuthException("Invalid token: " + ex.getMessage());
        }
    }

    /** Parsed claims subset used by filter to populate SecurityContext. */
    public static final class Claims {
        public String userId;
        public String email;
        public String role;
        public String type;        // "access" or "refresh"
        public long expiresAt;     // unix millis
    }
}
