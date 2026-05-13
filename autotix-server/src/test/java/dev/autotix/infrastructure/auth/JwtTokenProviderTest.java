package dev.autotix.infrastructure.auth;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT round-trip tests.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtTokenProviderTest {

    @Autowired
    JwtTokenProvider tokenProvider;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.rehydrate(
                new UserId("42"),
                "jwt-test@test.local",
                "JwtUser",
                "hash",
                UserRole.AGENT,
                true,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void issueAndVerify_roundTrip() {
        String token = tokenProvider.issueAccess(testUser);
        assertNotNull(token);

        JwtTokenProvider.Claims claims = tokenProvider.verify(token);

        assertEquals("42", claims.userId);
        assertEquals("jwt-test@test.local", claims.email);
        assertEquals("AGENT", claims.role);
        assertEquals("access", claims.type);
        assertTrue(claims.expiresAt > System.currentTimeMillis());
    }

    @Test
    void issueRefresh_claimsCorrect() {
        String token = tokenProvider.issueRefresh(testUser);
        JwtTokenProvider.Claims claims = tokenProvider.verify(token);

        assertEquals("refresh", claims.type);
        assertEquals("42", claims.userId);
        // refresh expiry should be well beyond access expiry
        assertTrue(claims.expiresAt > System.currentTimeMillis());
    }

    @Test
    void tamperedToken_rejected() {
        String token = tokenProvider.issueAccess(testUser);
        // Corrupt the signature part
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignature";

        assertThrows(AutotixException.AuthException.class,
                () -> tokenProvider.verify(tampered));
    }

    @Test
    void completelyInvalidToken_rejected() {
        assertThrows(AutotixException.AuthException.class,
                () -> tokenProvider.verify("not.a.jwt"));
    }

    @Test
    void shortSecret_failsFastAtStartup() {
        AuthConfig cfg = new AuthConfig();
        cfg.setJwtSecret("tooshort");
        cfg.setAccessTtlMinutes(10);
        cfg.setRefreshTtlDays(1);
        JwtTokenProvider bad = new JwtTokenProvider(cfg);

        assertThrows(IllegalStateException.class, bad::init);
    }
}
