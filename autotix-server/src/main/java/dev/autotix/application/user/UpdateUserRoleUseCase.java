package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import org.springframework.stereotype.Service;

/**
 * Change a user's role. Enforces "must have at least one ADMIN".
 * Throws ValidationException if demoting the last admin.
 */
@Service
public class UpdateUserRoleUseCase {

    private final UserRepository userRepository;

    public UpdateUserRoleUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void updateRole(UserId targetUserId, UserRole newRole) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AutotixException.ValidationException(
                        "User not found: " + targetUserId.value()));

        // Last-admin guard: cannot demote the only remaining admin
        if (user.role() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new AutotixException.ValidationException(
                        "Cannot demote the last admin. Promote another user first.");
            }
        }

        user.changeRole(newRole);
        userRepository.save(user);
    }
}
