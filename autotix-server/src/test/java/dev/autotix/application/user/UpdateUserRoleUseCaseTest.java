package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Role-change invariants.
 */
@ExtendWith(MockitoExtension.class)
class UpdateUserRoleUseCaseTest {

    @Mock
    UserRepository userRepository;

    UpdateUserRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateUserRoleUseCase(userRepository);
    }

    private User adminUser(String id) {
        return User.rehydrate(new UserId(id), "admin" + id + "@test.local", "Admin" + id,
                "hash", UserRole.ADMIN, true, null, Instant.now(), Instant.now());
    }

    private User agentUser(String id) {
        return User.rehydrate(new UserId(id), "agent" + id + "@test.local", "Agent" + id,
                "hash", UserRole.AGENT, true, null, Instant.now(), Instant.now());
    }

    @Test
    void demotingLastAdmin_throws() {
        User admin = adminUser("1");
        when(userRepository.findById(new UserId("1"))).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L); // only one admin

        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.updateRole(new UserId("1"), UserRole.AGENT));

        // save must not be called
        verify(userRepository, never()).save(any());
    }

    @Test
    void demotingOneOfMany_succeeds() {
        User admin = adminUser("1");
        when(userRepository.findById(new UserId("1"))).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(2L); // two admins
        when(userRepository.save(admin)).thenReturn(new UserId("1"));

        assertDoesNotThrow(() -> useCase.updateRole(new UserId("1"), UserRole.AGENT));

        assertEquals(UserRole.AGENT, admin.role());
        verify(userRepository).save(admin);
    }

    @Test
    void promotingAgentToAdmin_succeeds() {
        User agent = agentUser("2");
        when(userRepository.findById(new UserId("2"))).thenReturn(Optional.of(agent));
        when(userRepository.save(agent)).thenReturn(new UserId("2"));

        assertDoesNotThrow(() -> useCase.updateRole(new UserId("2"), UserRole.ADMIN));

        assertEquals(UserRole.ADMIN, agent.role());
        // countByRole should not be checked when promoting (not demoting an admin)
        verify(userRepository, never()).countByRole(any());
    }

    @Test
    void userNotFound_throws() {
        when(userRepository.findById(new UserId("99"))).thenReturn(Optional.empty());
        assertThrows(AutotixException.ValidationException.class,
                () -> useCase.updateRole(new UserId("99"), UserRole.AGENT));
    }
}
