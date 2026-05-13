package dev.autotix.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * TODO: Repository port for User aggregate.
 */
public interface UserRepository {

    UserId save(User user);

    Optional<User> findById(UserId id);

    /** TODO: case-insensitive email lookup */
    Optional<User> findByEmail(String email);

    /** TODO: list all users for admin UI */
    List<User> findAll();

    /** TODO: count of ADMIN users — used to enforce "cannot demote last admin" */
    long countByRole(UserRole role);

    /** TODO: soft delete (set enabled=false) */
    void disable(UserId id);
}
