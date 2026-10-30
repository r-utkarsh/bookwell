package com.bookwell.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final Pattern VALID = Pattern.compile("^(?=.*\\p{L})(?=.*\\d)[^\\p{Cc}\\u2028\\u2029]{10,}$");
    public static final String MESSAGE =
            "Password must be 10-72 characters, contain at least one letter and one number, and not be a common password.";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return isValid(password, null);
    }

    public static boolean isValid(String password, String email) {
        if (password == null) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return false;
        }
        if (!VALID.matcher(password).matches()) {
            return false;
        }
        if (CommonPasswords.contains(password)) {
            return false;
        }
        if (email != null && !email.isBlank()) {
            String prefix = email.split("@")[0].toLowerCase(Locale.ROOT);
            if (prefix.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(prefix)) {
                return false;
            }
        }
        return true;
    }
}
