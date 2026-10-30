package com.bookwell.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void convertsPostgresUriAndDecodesCredentials() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "postgresql://bookwell:p%40ss%3Aword@db.example.com:5432/bookwell?sslmode=require");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/bookwell?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("bookwell");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("p@ss:word");
    }

    @Test
    void supportsThePostgresSchemeAndDefaultPort() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "postgres://user:secret@database.internal/bookwell");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://database.internal/bookwell");
    }

    @Test
    void leavesJdbcUrlsAndNonPostgresValuesAlone() {
        MockEnvironment jdbc = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://localhost:5432/bookwell");
        processor.postProcessEnvironment(jdbc, null);
        assertThat(jdbc.getProperty("spring.datasource.url")).isNull();

        MockEnvironment other = new MockEnvironment()
                .withProperty("DB_URL", "mysql://localhost/bookwell");
        processor.postProcessEnvironment(other, null);
        assertThat(other.getProperty("spring.datasource.url")).isNull();
    }

    @Test
    void supportsDatabaseUrlAndExplicitCredentialOverrides() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://uri-user:uri-pass@db.example.com/bookwell")
                .withProperty("DB_USERNAME", "explicit-user");

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com/bookwell");
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("uri-pass");
    }

    @Test
    void rejectsAUrlWithoutAHost() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "postgresql:///bookwell");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host");
    }

    @Test
    void isRegisteredWithSpringBoot() throws Exception {
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/spring.factories");
        boolean registered = false;
        while (resources.hasMoreElements()) {
            Properties factories = new Properties();
            try (InputStream input = resources.nextElement().openStream()) {
                factories.load(input);
            }
            String value = factories.getProperty(EnvironmentPostProcessor.class.getName(), "");
            if (Arrays.stream(value.split(","))
                    .map(String::trim)
                    .anyMatch(DatabaseUrlEnvironmentPostProcessor.class.getName()::equals)) {
                registered = true;
                break;
            }
        }
        assertThat(registered)
                .as("DatabaseUrlEnvironmentPostProcessor must be listed in META-INF/spring.factories")
                .isTrue();
    }
}
