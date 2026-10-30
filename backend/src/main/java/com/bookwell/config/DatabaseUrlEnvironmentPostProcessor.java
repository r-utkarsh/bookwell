package com.bookwell.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Accepts hosted PostgreSQL connection strings such as Render's
 * {@code postgresql://user:password@host:5432/database} and exposes them as
 * JDBC datasource properties. Native JDBC URLs are left untouched.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "bookwellDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = firstNonBlank(environment.getProperty("DB_URL"), environment.getProperty("DATABASE_URL"));
        if (raw == null || raw.startsWith("jdbc:")) {
            return;
        }
        String scheme = raw.toLowerCase(Locale.ROOT);
        if (!scheme.startsWith("postgres://") && !scheme.startsWith("postgresql://")) {
            return;
        }

        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("DB_URL is not a valid PostgreSQL connection URI.", ex);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalStateException("DB_URL must include a PostgreSQL host.");
        }

        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() != -1) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        jdbcUrl.append(path == null || path.isBlank() ? "/" : path);
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        Map<String, Object> values = new HashMap<>();
        values.put("spring.datasource.url", jdbcUrl.toString());

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            String[] credentials = userInfo.split(":", 2);
            if (isBlank(environment.getProperty("DB_USERNAME"))) {
                values.put("spring.datasource.username", decode(credentials[0]));
            }
            if (credentials.length > 1 && isBlank(environment.getProperty("DB_PASSWORD"))) {
                values.put("spring.datasource.password", decode(credentials[1]));
            }
        }

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second != null && !second.isBlank() ? second.trim() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
