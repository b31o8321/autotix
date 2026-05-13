package dev.autotix.infrastructure.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds autotix.auth.* from application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "autotix.auth")
public class AuthConfig {
    private String jwtSecret;
    private int accessTtlMinutes = 60;
    private int refreshTtlDays = 30;
    private String bootstrapAdminEmail;
    private String bootstrapAdminPassword;
}
