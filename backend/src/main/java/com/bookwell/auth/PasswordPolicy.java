package com.bookwell.auth;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final Pattern VALID = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{10,72}$");
    public static final String MESSAGE =
            "Password must be 10-72 characters, contain a letter and a number, and use only printable characters without spaces.";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && VALID.matcher(password).matches()
                && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
