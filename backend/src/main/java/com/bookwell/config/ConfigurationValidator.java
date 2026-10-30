package com.bookwell.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

/**
 * Fails fast when production is configured with unsafe defaults.
 */
@Component
public class ConfigurationValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationValidator.class);

    private final AppProperties properties;
    private final Environment environment;

    public ConfigurationValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateCommon();
        if (properties.isProduction()) {
            validateProduction();
        } else {
            log.info("Running in {} mode. Production safety checks are not enforced.", properties.env());
        }
    }

    private void validateCommon() {
        String sameSite = properties.security().cookieSameSite();
        if (!"Lax".equalsIgnoreCase(sameSite) && !"Strict".equalsIgnoreCase(sameSite)
                && !"None".equalsIgnoreCase(sameSite)) {
            throw new IllegalStateException("AUTH_COOKIE_SAME_SITE must be Lax, Strict, or None.");
        }
        if ("None".equalsIgnoreCase(sameSite) && !properties.security().cookieSecure()) {
            throw new IllegalStateException("SameSite=None cookies require AUTH_COOKIE_SECURE=true.");
        }
        if (properties.security().corsAllowedOrigins().stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("Wildcard CORS origins are not permitted.");
        }
        if (properties.seed().enabled()) {
            require(properties.seed().adminEmail(), "SEED_ADMIN_EMAIL");
            require(properties.seed().adminPassword(), "SEED_ADMIN_PASSWORD");
            require(properties.seed().providerEmail(), "SEED_PROVIDER_EMAIL");
            require(properties.seed().providerPassword(), "SEED_PROVIDER_PASSWORD");
            require(properties.seed().customerEmail(), "SEED_CUSTOMER_EMAIL");
            require(properties.seed().customerPassword(), "SEED_CUSTOMER_PASSWORD");
            if (properties.isProduction()) {
                throw new IllegalStateException("Demo seeding cannot be enabled in production.");
            }
        }
        boolean hasBootstrapEmail = properties.bootstrap().adminEmail() != null
                && !properties.bootstrap().adminEmail().isBlank();
        boolean hasBootstrapPassword = properties.bootstrap().adminPassword() != null
                && !properties.bootstrap().adminPassword().isBlank();
        if (hasBootstrapEmail != hasBootstrapPassword) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD must be set together.");
        }
    }

    private void validateProduction() {
        require(properties.security().jwtSecret(), "JWT_SECRET");
        if (!properties.security().cookieSecure()) {
            throw new IllegalStateException("Production requires AUTH_COOKIE_SECURE=true.");
        }
        if (properties.security().corsAllowedOrigins().isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must be configured in production.");
        }
        if (properties.notifications().mailMode() != AppProperties.MailMode.SMTP) {
            throw new IllegalStateException(
                    "MAIL_MODE=log is not allowed in production because it does not deliver email. Set MAIL_MODE=smtp.");
        }
        require(properties.notifications().smtpHost(), "MAIL_HOST");
        require(properties.notifications().fromAddress(), "MAIL_FROM");
        try {
            URI publicUrl = URI.create(properties.notifications().appPublicUrl());
            if (!"https".equalsIgnoreCase(publicUrl.getScheme())) {
                throw new IllegalStateException("APP_PUBLIC_URL must use HTTPS in production.");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("APP_PUBLIC_URL must be a valid URL.", ex);
        }
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto", "validate");
        if (!"validate".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException("Production requires spring.jpa.hibernate.ddl-auto=validate.");
        }
        if ("localhost".equalsIgnoreCase(properties.notifications().smtpHost())) {
            throw new IllegalStateException("MAIL_HOST must not point at localhost in production.");
        }
        log.info("Production configuration validation passed.");
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured.");
        }
    }

    public static String normalizedEnv(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
