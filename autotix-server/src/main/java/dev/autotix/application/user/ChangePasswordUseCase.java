package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.infrastructure.auth.PasswordHasher;
import org.springframework.stereotype.Service;

/**
 * User changes their own password.
 * - Verifies current raw password before accepting the change.
 * - Hashes the new password and persists.
 */
@Service
public class ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public ChangePasswordUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public void changePassword(UserId userId, String currentRawPassword, String newRawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AutotixException.ValidationException(
                        "User not found: " + userId.value()));

        if (!passwordHasher.matches(currentRawPassword, user.passwordHash())) {
            throw new AutotixException.AuthException("Current password is incorrect");
        }

        if (newRawPassword == null || newRawPassword.isEmpty()) {
            throw new AutotixException.ValidationException("New password must not be blank");
        }

        String newHash = passwordHasher.hash(newRawPassword);
        user.changePassword(newHash);
        userRepository.save(user);
    }
}
