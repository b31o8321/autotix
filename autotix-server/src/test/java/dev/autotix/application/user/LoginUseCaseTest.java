package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import dev.autotix.infrastructure.auth.AuthConfig;
import dev.autotix.infrastructure.auth.JwtTokenProvider;
import dev.autotix.infrastructure.auth.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Login flow tests.
 */
@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordHasher passwordHasher;

    // Use real JwtTokenProvider with test config so we can verify actual tokens
    JwtTokenProvider tokenProvider;
    LoginUseCase loginUseCase;

    private User enabledUser;

    @BeforeEach
    void setUp() {
        AuthConfig cfg = new AuthConfig();
        cfg.setJwtSecret("test-jwt-secret-must-be-at-least-32-characters-long");
        cfg.setAccessTtlMinutes(10);
        cfg.setRefreshTtlDays(1);
        tokenProvider = new JwtTokenProvider(cfg);
        tokenProvider.init();

        loginUseCase = new LoginUseCase(userRepository, passwordHasher, tokenProvider);

        // Build a rehydrated user with a known id so JWT subject is set
        enabledUser = User.rehydrate(
                new UserId("1"),
                "admin@test.local",
                "Admin",
                "$2a$10$hashedpw",
                UserRole.ADMIN,
                true,
                null,
                java.time.Instant.now(),
                java.time.Instant.now()
        );
    }

    @Test
    void login_valid_returnsTokenPair() {
        when(userRepository.findByEmail("admin@test.local")).thenReturn(Optional.of(enabledUser));
        when(passwordHasher.matches("correctpw", "$2a$10$hashedpw")).thenReturn(true);
        when(userRepository.save(enabledUser)).thenReturn(enabledUser.id());

        LoginUseCase.TokenPair pair = loginUseCase.login("admin@test.local", "correctpw");

        assertNotNull(pair.accessToken);
        assertNotNull(pair.refreshToken);
        assertTrue(pair.accessExpiresAt > System.currentTimeMillis());
        assertTrue(pair.refreshExpiresAt > pair.accessExpiresAt);

        // Verify lastLoginAt was stamped (recordLogin called → save called)
        verify(userRepository).save(enabledUser);
        assertNotNull(enabledUser.lastLoginAt());
    }

    @Test
    void login_wrongPassword_throws() {
        when(userRepository.findByEmail("admin@test.local")).thenReturn(Optional.of(enabledUser));
        when(passwordHasher.matches("wrongpw", "$2a$10$hashedpw")).thenReturn(false);

        assertThrows(AutotixException.AuthException.class,
                () -> loginUseCase.login("admin@test.local", "wrongpw"));
    }

    @Test
    void login_disabledUser_throws() {
        User disabledUser = User.rehydrate(
                new UserId("2"), "disabled@test.local", "Disabled",
                "$2a$10$hashedpw", UserRole.AGENT, false,
                null, java.time.Instant.now(), java.time.Instant.now());
        when(userRepository.findByEmail("disabled@test.local")).thenReturn(Optional.of(disabledUser));

        assertThrows(AutotixException.AuthException.class,
                () -> loginUseCase.login("disabled@test.local", "anypassword"));

        // password check should not be called for disabled user
        verifyNoInteractions(passwordHasher);
    }

    @Test
    void login_unknownEmail_throws() {
        when(userRepository.findByEmail("unknown@test.local")).thenReturn(Optional.empty());

        assertThrows(AutotixException.AuthException.class,
                () -> loginUseCase.login("unknown@test.local", "anypassword"));
    }
}
