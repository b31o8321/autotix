package dev.autotix.application.user;

import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * List all users for the admin page.
 */
@Service
public class ListUsersUseCase {

    private final UserRepository userRepository;

    public ListUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> list() {
        return userRepository.findAll();
    }
}
