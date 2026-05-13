package dev.autotix.infrastructure.persistence.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for User. Table name `app_user` (avoid reserved `user`).
 */
@Data
@TableName("app_user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    @TableField("display_name")
    private String displayName;

    /** BCrypt-encoded */
    @TableField("password_hash")
    private String passwordHash;

    /** ADMIN / AGENT / VIEWER */
    private String role;

    private Boolean enabled;

    @TableField("last_login_at")
    private Instant lastLoginAt;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
