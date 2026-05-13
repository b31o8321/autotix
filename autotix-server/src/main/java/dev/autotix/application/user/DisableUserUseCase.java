package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import org.springframework.stereotype.Service;

/**
 * Disable a user (soft delete). Applies same last-admin invariant as role change.
 */
@Service
public class DisableUserUseCase {

    private final UserRepository userRepository;

    public DisableUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void disable(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AutotixException.ValidationException(
                        "User not found: " + userId.value()));

        // Last-admin guard
        if (user.role() == UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new AutotixException.ValidationException(
                        "Cannot disable the last admin. Promote another user first.");
            }
        }

        userRepository.disable(userId);
    }
}
