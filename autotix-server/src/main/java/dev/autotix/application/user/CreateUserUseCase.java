package dev.autotix.application.user;

import dev.autotix.domain.AutotixException;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import dev.autotix.infrastructure.auth.PasswordHasher;
import org.springframework.stereotype.Service;

/**
 * Admin creates a new user with an assigned role.
 */
@Service
public class CreateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public CreateUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public UserId create(String email, String displayName, String rawPassword, UserRole role) {
        if (email == null || email.trim().isEmpty()) {
            throw new AutotixException.ValidationException("email must not be blank");
        }
        // Reject duplicate email
        if (userRepository.findByEmail(email).isPresent()) {
            throw new AutotixException.ValidationException("Email already in use: " + email);
        }
        String hash = passwordHasher.hash(rawPassword);
        User user = User.register(email, displayName, hash, role);
        return userRepository.save(user);
    }
}
