package com.bookwell.auth;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CommonPasswords {

    private static final Set<String> PASSWORDS = new HashSet<>();

    static {
        try (InputStream in = CommonPasswords.class.getResourceAsStream("/common-passwords.txt")) {
            if (in != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim().toLowerCase(Locale.ROOT);
                        if (!trimmed.isBlank() && !trimmed.startsWith("#")) {
                            PASSWORDS.add(trimmed);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        PASSWORDS.addAll(Set.of(
                "password123", "password1234", "password2024", "password2025", "password2026",
                "1234567890", "123456789a", "qwertyuiop1", "admin123456", "welcome1234",
                "letmein1234", "changeme123", "iloveyou123"
        ));
    }

    private CommonPasswords() {
    }

    public static boolean contains(String password) {
        if (password == null) {
            return false;
        }
        return PASSWORDS.contains(password.toLowerCase(Locale.ROOT));
    }
}
