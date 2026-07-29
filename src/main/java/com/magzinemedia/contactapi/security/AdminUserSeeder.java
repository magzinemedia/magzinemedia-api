package com.magzinemedia.contactapi.security;

import com.magzinemedia.contactapi.model.AdminUser;
import com.magzinemedia.contactapi.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultUsername;
    private final String defaultPassword;

    public AdminUserSeeder(
        AdminUserRepository repository,
        PasswordEncoder passwordEncoder,
        @Value("${app.admin.username:}") String defaultUsername,
        @Value("${app.admin.password:}") String defaultPassword
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.defaultUsername = defaultUsername;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        if (defaultUsername.isBlank() || defaultPassword.isBlank()) {
            log.warn("No admin user exists yet, and APP_ADMIN_USERNAME/APP_ADMIN_PASSWORD are not set. "
                + "Set both environment variables and restart to create the first admin account.");
            return;
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(defaultUsername);
        admin.setPasswordHash(passwordEncoder.encode(defaultPassword));
        admin.setRole("ADMIN");
        repository.save(admin);

        log.info("Created initial admin user '{}'", defaultUsername);
    }
}
