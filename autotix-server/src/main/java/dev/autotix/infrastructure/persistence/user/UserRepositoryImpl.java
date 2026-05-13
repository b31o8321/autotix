package dev.autotix.infrastructure.persistence.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserId;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import dev.autotix.infrastructure.persistence.user.mapper.UserMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implements UserRepository port via MyBatis Plus.
 */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    public UserRepositoryImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserId save(User user) {
        UserEntity entity = toEntity(user);
        if (user.id() == null) {
            // INSERT — clear id so AUTO_INCREMENT kicks in
            entity.setId(null);
            userMapper.insert(entity);
            // entity.getId() is populated by MyBatis Plus after insert
            UserId newId = new UserId(String.valueOf(entity.getId()));
            // Inject id back into domain object
            user.setId(newId);
            return newId;
        } else {
            // UPDATE
            userMapper.updateById(entity);
            return user.id();
        }
    }

    @Override
    public Optional<User> findById(UserId id) {
        UserEntity entity = userMapper.selectById(Long.parseLong(id.value()));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.eq("email", email.toLowerCase().trim());
        UserEntity entity = userMapper.selectOne(qw);
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(null)
                .stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long countByRole(UserRole role) {
        QueryWrapper<UserEntity> qw = new QueryWrapper<>();
        qw.eq("role", role.name()).eq("enabled", true);
        return userMapper.selectCount(qw);
    }

    @Override
    public void disable(UserId id) {
        UserEntity entity = new UserEntity();
        entity.setId(Long.parseLong(id.value()));
        entity.setEnabled(false);
        entity.setUpdatedAt(Instant.now());
        userMapper.updateById(entity);
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    private UserEntity toEntity(User u) {
        UserEntity e = new UserEntity();
        if (u.id() != null) {
            e.setId(Long.parseLong(u.id().value()));
        }
        e.setEmail(u.email().toLowerCase().trim());
        e.setDisplayName(u.displayName());
        e.setPasswordHash(u.passwordHash());
        e.setRole(u.role().name());
        e.setEnabled(u.isEnabled());
        e.setLastLoginAt(u.lastLoginAt());
        e.setCreatedAt(u.createdAt());
        e.setUpdatedAt(u.updatedAt());
        return e;
    }

    private User toDomain(UserEntity e) {
        return User.rehydrate(
                new UserId(String.valueOf(e.getId())),
                e.getEmail(),
                e.getDisplayName(),
                e.getPasswordHash(),
                UserRole.valueOf(e.getRole()),
                Boolean.TRUE.equals(e.getEnabled()),
                e.getLastLoginAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
