package com.bookwell.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void allowsPassphrasesWithSpaces() {
        assertThat(PasswordPolicy.isValid("correct horse battery staple 1")).isTrue();
        assertThat(PasswordPolicy.isValid("Valid Pass 2026")).isTrue();
    }

    @Test
    void rejectsTooShortOrExceeding72Bytes() {
        assertThat(PasswordPolicy.isValid("Short1")).isFalse();
        String tooLong = "A".repeat(70) + "123"; // 73 chars
        assertThat(PasswordPolicy.isValid(tooLong)).isFalse();
    }

    @Test
    void rejectsMissingLettersOrNumbers() {
        assertThat(PasswordPolicy.isValid("123456789012")).isFalse();
        assertThat(PasswordPolicy.isValid("abcdefghijklm")).isFalse();
    }

    @Test
    void rejectsCommonPasswords() {
        assertThat(PasswordPolicy.isValid("password123")).isFalse();
        assertThat(PasswordPolicy.isValid("admin123456")).isFalse();
    }

    @Test
    void rejectsPasswordContainingEmailPrefix() {
        assertThat(PasswordPolicy.isValid("alice_Secret123", "alice@example.com")).isFalse();
        assertThat(PasswordPolicy.isValid("CompletelyDiff123", "alice@example.com")).isTrue();
    }
}
