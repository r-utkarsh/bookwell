package com.bookwell.seed;

import com.bookwell.auth.PasswordPolicy;
import com.bookwell.auth.UserAccountService;
import com.bookwell.config.AppProperties;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Creates the first ADMIN from environment variables. This never resets an existing password.
 * Remove BOOTSTRAP_ADMIN_* variables after the account has been provisioned.
 */
@Component
@Order(1)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AppProperties properties, UserRepository userRepository,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.bootstrap().adminEmail();
        String password = properties.bootstrap().adminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD does not satisfy the password policy: "
                    + PasswordPolicy.MESSAGE);
        }
        String normalized = UserAccountService.normalizeEmail(email);
        Optional<User> existing = userRepository.findByEmail(normalized);
        if (existing.isPresent()) {
            log.info("Bootstrap admin {} already exists; no changes made.", normalized);
            return;
        }
        String name = properties.bootstrap().adminName() == null || properties.bootstrap().adminName().isBlank()
                ? "Platform Administrator" : properties.bootstrap().adminName().trim();
        User admin = User.create(normalized, passwordEncoder.encode(password), Role.ADMIN, name, null);
        admin.markEmailVerified(java.time.Instant.now());
        userRepository.save(admin);
        log.info("Bootstrap administrator {} created. Remove BOOTSTRAP_ADMIN_EMAIL and "
                + "BOOTSTRAP_ADMIN_PASSWORD from the environment after verifying sign-in.", normalized);
    }
}
