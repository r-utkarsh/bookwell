package com.bookwell.auth;

import com.bookwell.common.exception.AppExceptions;
import com.bookwell.common.security.AuthUser;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderProfileRepository;
import com.bookwell.provider.ProviderStatus;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;

    public UserAccountService(UserRepository userRepository, ProviderProfileRepository providerRepository) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
    }

    public AuthDtos.UserSummary summary(User user) {
        ProviderProfile provider = user.getRole() == Role.PROVIDER
                ? providerRepository.findByUserId(user.getId()).orElse(null)
                : null;
        return new AuthDtos.UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getPhone(),
                user.isEnabled(),
                user.isEmailVerified(),
                provider != null ? provider.getId() : null,
                provider != null ? provider.getStatus() : null
        );
    }

    public AuthDtos.UserSummary summary(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppExceptions.unauthorized("Your account is no longer available."));
        return summary(user);
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static void validatePasswordBytes(String password) {
        if (password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw AppExceptions.badRequest("WEAK_PASSWORD", "Password must be at most 72 bytes.");
        }
    }
}
