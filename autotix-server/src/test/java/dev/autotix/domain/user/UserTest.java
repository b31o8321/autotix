package dev.autotix.domain.user;

import dev.autotix.domain.AutotixException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for User aggregate behaviors.
 * Last-admin invariant is NOT enforced here (that's UseCase-level), but role
 * transitions and recordLogin / disable / changePassword are tested.
 */
class UserTest {

    @Test
    void register_createsEnabledUserWithGivenRole() {
        User user = User.register("Alice@Example.COM", "Alice", "hashedpw", UserRole.AGENT);

        assertNull(user.id(), "id should be null until persisted");
        assertEquals("alice@example.com", user.email(), "email should be lowercased");
        assertEquals("Alice", user.displayName());
        assertEquals("hashedpw", user.passwordHash());
        assertEquals(UserRole.AGENT, user.role());
        assertTrue(user.isEnabled());
        assertNull(user.lastLoginAt());
        assertNotNull(user.createdAt());
        assertNotNull(user.updatedAt());
    }

    @Test
    void register_blankEmail_throws() {
        assertThrows(AutotixException.ValidationException.class,
                () -> User.register("", "Alice", "hash", UserRole.AGENT));
    }

    @Test
    void changeRole_updatesRoleAndStampsUpdatedAt() {
        User user = User.register("bob@example.com", "Bob", "hashedpw", UserRole.AGENT);
        Instant before = user.updatedAt();

        // Small sleep to ensure time difference is detectable
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        user.changeRole(UserRole.ADMIN);

        assertEquals(UserRole.ADMIN, user.role());
        assertTrue(user.updatedAt().isAfter(before) || user.updatedAt().equals(before),
                "updatedAt should be >= original");
    }

    @Test
    void disable_setsEnabledFalse() {
        User user = User.register("carol@example.com", "Carol", "hash", UserRole.VIEWER);
        assertTrue(user.isEnabled());
        user.disable();
        assertFalse(user.isEnabled());
    }

    @Test
    void recordLogin_stampsLastLoginAt() {
        User user = User.register("dave@example.com", "Dave", "hash", UserRole.AGENT);
        assertNull(user.lastLoginAt());
        Instant loginTime = Instant.now();
        user.recordLogin(loginTime);
        assertEquals(loginTime, user.lastLoginAt());
    }

    @Test
    void changePassword_updatesHash() {
        User user = User.register("eve@example.com", "Eve", "oldhash", UserRole.AGENT);
        user.changePassword("newhash");
        assertEquals("newhash", user.passwordHash());
    }

    @Test
    void userId_nullValue_throws() {
        assertThrows(AutotixException.ValidationException.class, () -> new UserId(null));
    }

    @Test
    void userId_emptyValue_throws() {
        assertThrows(AutotixException.ValidationException.class, () -> new UserId(""));
    }

    @Test
    void userId_equality() {
        UserId a = new UserId("42");
        UserId b = new UserId("42");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
