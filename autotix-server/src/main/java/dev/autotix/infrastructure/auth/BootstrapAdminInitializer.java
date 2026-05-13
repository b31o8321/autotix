package dev.autotix.infrastructure.auth;

import dev.autotix.domain.user.User;
import dev.autotix.domain.user.UserRepository;
import dev.autotix.domain.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On first boot when no ADMIN users exist, create the bootstrap admin from
 * autotix.auth.bootstrap-admin-* config so the operator can log in.
 * Logs a one-time WARN reminding to change the password.
 */
@Component
public class BootstrapAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final AuthConfig authConfig;
    private final PasswordHasher passwordHasher;

    public BootstrapAdminInitializer(UserRepository userRepository,
                                     AuthConfig authConfig,
                                     PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.authConfig = authConfig;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countByRole(UserRole.ADMIN) == 0) {
            String email = authConfig.getBootstrapAdminEmail();
            String rawPassword = authConfig.getBootstrapAdminPassword();
            String hash = passwordHasher.hash(rawPassword);
            User admin = User.register(email, "Admin", hash, UserRole.ADMIN);
            userRepository.save(admin);
            log.warn("Bootstrap admin created (email={}) with default password — change it immediately!",
                    email);
        }
    }
}
