package dev.autotix.interfaces.admin;

import dev.autotix.application.user.CreateUserUseCase;
import dev.autotix.application.user.DisableUserUseCase;
import dev.autotix.application.user.ListUsersUseCase;
import dev.autotix.application.user.UpdateUserRoleUseCase;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRole;
import dev.autotix.interfaces.admin.dto.CreateUserRequest;
import dev.autotix.interfaces.admin.dto.UserDTO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User management — ADMIN only.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final ListUsersUseCase listUsers;
    private final CreateUserUseCase createUser;
    private final UpdateUserRoleUseCase updateRole;
    private final DisableUserUseCase disableUser;

    public UserAdminController(ListUsersUseCase listUsers, CreateUserUseCase createUser,
                               UpdateUserRoleUseCase updateRole, DisableUserUseCase disableUser) {
        this.listUsers = listUsers;
        this.createUser = createUser;
        this.updateRole = updateRole;
        this.disableUser = disableUser;
    }

    @GetMapping
    public List<UserDTO> list() {
        return listUsers.list().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public UserDTO create(@RequestBody CreateUserRequest req) {
        UserRole role = UserRole.valueOf(req.role.toUpperCase());
        UserId id = createUser.create(req.email, req.displayName, req.password, role);
        // Re-fetch to return full DTO
        User user = listUsers.list().stream()
                .filter(u -> u.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found after creation"));
        return toDTO(user);
    }

    @PutMapping("/{userId}/role")
    public void changeRole(@PathVariable String userId, @RequestParam String role) {
        UserRole newRole = UserRole.valueOf(role.toUpperCase());
        updateRole.updateRole(new UserId(userId), newRole);
    }

    @DeleteMapping("/{userId}")
    public void disable(@PathVariable String userId) {
        disableUser.disable(new UserId(userId));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UserDTO toDTO(User u) {
        UserDTO dto = new UserDTO();
        dto.id = u.id().value();
        dto.email = u.email();
        dto.displayName = u.displayName();
        dto.role = u.role().name();
        dto.enabled = u.isEnabled();
        dto.lastLoginAt = u.lastLoginAt();
        dto.createdAt = u.createdAt();
        return dto;
    }
}
