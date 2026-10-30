package com.bookwell.auth;

import com.bookwell.provider.ProviderStatus;
import com.bookwell.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Enter a valid email address.")
            @Size(max = 320, message = "Email is too long.")
            String email,

            @NotBlank(message = "Password is required.")
            String password,

            @NotBlank(message = "Your name is required.")
            @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String fullName,

            @Size(max = 30, message = "Phone number is too long.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String phone
    ) {
    }

    public record ProviderRegisterRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Enter a valid email address.")
            @Size(max = 320, message = "Email is too long.")
            String email,

            @NotBlank(message = "Password is required.")
            String password,

            @NotBlank(message = "Your name is required.")
            @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String fullName,

            @Size(max = 30, message = "Phone number is too long.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String phone,

            @NotBlank(message = "Business name is required.")
            @Size(min = 2, max = 160, message = "Business name must be between 2 and 160 characters.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String businessName,

            @NotBlank(message = "Public display name is required.")
            @Size(min = 2, max = 140, message = "Display name must be between 2 and 140 characters.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String displayName,

            @NotBlank(message = "Choose a business category.")
            @Size(max = 60, message = "Category is too long.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String category,

            @Size(max = 100, message = "City is too long.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String city,

            @Size(max = 64, message = "Time zone is too long.")
            @Pattern(regexp = "^[^\\p{Cc}\\u2028\\u2029]*$", message = "Contains invalid characters.")
            String timeZone
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "Email is required.")
            @Email(message = "Enter a valid email address.")
            @Size(max = 320, message = "Email is too long.")
            String email,

            @NotBlank(message = "Password is required.")
            @Size(max = 72, message = "Password is too long.")
            String password
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserSummary user
    ) {
    }

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            Role role,
            String phone,
            boolean enabled,
            boolean emailVerified,
            UUID providerId,
            ProviderStatus providerStatus
    ) {
    }

    public record VerifyEmailRequest(
            @NotBlank(message = "Verification token is required.")
            String token
    ) {
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
