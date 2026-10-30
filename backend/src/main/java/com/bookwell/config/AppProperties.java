package com.bookwell.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String env,
        Security security,
        Booking booking,
        Notifications notifications,
        Seed seed,
        Bootstrap bootstrap,
        RateLimit rateLimit
) {
    public record Security(
            String jwtSecret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            boolean cookieSecure,
            String cookieSameSite,
            String cookieDomain,
            List<String> corsAllowedOrigins,
            String trustedClientIpHeader,
            String refreshCookieName
    ) {
    }

    public record Booking(
            String defaultTimeZone,
            String defaultCurrency,
            int startIntervalMinutes,
            Duration cancellationCutoff,
            Duration minLeadTime,
            int maxAdvanceDays
    ) {
    }

    public enum MailMode {
        LOG, SMTP
    }

    public record Notifications(
            boolean enabled,
            MailMode mailMode,
            Duration reminderLeadTime,
            int maxAttempts,
            long pollIntervalMs,
            String fromAddress,
            String fromName,
            String appPublicUrl,
            String smtpHost,
            int smtpPort,
            String smtpUsername,
            String smtpPassword,
            boolean smtpStartTls,
            boolean smtpAuth,
            boolean workerEnabled
    ) {
    }

    public record Seed(
            boolean enabled,
            String adminEmail,
            String adminPassword,
            String adminName,
            String providerEmail,
            String providerPassword,
            String customerEmail,
            String customerPassword
    ) {
    }

    public record Bootstrap(
            String adminEmail,
            String adminPassword,
            String adminName
    ) {
    }

    public record RateLimit(
            boolean enabled,
            int authRequestsPerMinute,
            int refreshRequestsPerMinute
    ) {
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(env) || "prod".equalsIgnoreCase(env);
    }
}
