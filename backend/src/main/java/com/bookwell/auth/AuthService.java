package com.bookwell.auth;

import com.bookwell.common.exception.AppExceptions;
import com.bookwell.config.AppProperties;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderProfileRepository;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;
    private final RefreshTokenSessionRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAccountService accountService;
    private final AppProperties properties;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final EmailVerificationService emailVerificationService;
    private final String dummyHash;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       ProviderProfileRepository providerRepository,
                       RefreshTokenSessionRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserAccountService accountService,
                       AppProperties properties,
                       LoginAttemptLimiter loginAttemptLimiter,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-1");
        this.jwtService = jwtService;
        this.accountService = accountService;
        this.properties = properties;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public AuthResult registerCustomer(AuthDtos.RegisterRequest request, RequestInfo info) {
        String email = UserAccountService.normalizeEmail(request.email());
        assertPassword(request.password(), email);
        if (userRepository.existsByEmail(email)) {
            throw AppExceptions.conflict("EMAIL_IN_USE", "An account with this email already exists.");
        }
        User user = User.create(email, passwordEncoder.encode(request.password()), Role.CUSTOMER,
                request.fullName().trim(), blankToNull(request.phone()));
        userRepository.save(user);
        String rawToken = emailVerificationService.issueFor(user);
        emailVerificationService.sendVerificationEmailAsync(user, rawToken);
        return createSession(user, UUID.randomUUID(), info);
    }

    @Transactional
    public AuthResult registerProvider(AuthDtos.ProviderRegisterRequest request, RequestInfo info) {
        String email = UserAccountService.normalizeEmail(request.email());
        assertPassword(request.password(), email);
        if (userRepository.existsByEmail(email)) {
            throw AppExceptions.conflict("EMAIL_IN_USE", "An account with this email already exists.");
        }
        String timeZone = request.timeZone() == null || request.timeZone().isBlank()
                ? properties.booking().defaultTimeZone()
                : request.timeZone().trim();
        validateTimeZone(timeZone);
        User user = User.create(email, passwordEncoder.encode(request.password()), Role.PROVIDER,
                request.fullName().trim(), blankToNull(request.phone()));
        userRepository.save(user);
        ProviderProfile provider = ProviderProfile.create(
                user,
                request.displayName().trim(),
                request.businessName().trim(),
                request.category().trim(),
                request.city() == null ? "" : request.city().trim(),
                timeZone,
                properties.booking().defaultCurrency()
        );
        providerRepository.save(provider);
        String rawToken = emailVerificationService.issueFor(user);
        emailVerificationService.sendVerificationEmailAsync(user, rawToken);
        return createSession(user, UUID.randomUUID(), info);
    }

    @Transactional(noRollbackFor = com.bookwell.common.exception.ApiException.class)
    public AuthResult refresh(String rawRefreshToken, RequestInfo info) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw AppExceptions.unauthorized("Your session has expired. Please sign in again.");
        }
        RefreshTokenSession session = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> AppExceptions.unauthorized("Your session has expired. Please sign in again."));
        Instant now = Instant.now();
        if (session.getRevokedAt() != null) {
            refreshTokenRepository.revokeFamily(session.getFamilyId(), now);
            throw AppExceptions.unauthorized("Your session is no longer valid. Please sign in again.");
        }
        if (!session.getExpiresAt().isAfter(now)) {
            session.setRevokedAt(now);
            refreshTokenRepository.save(session);
            throw AppExceptions.unauthorized("Your session has expired. Please sign in again.");
        }
        User user = session.getUser();
        if (!user.isEnabled()) {
            refreshTokenRepository.revokeFamily(session.getFamilyId(), now);
            throw AppExceptions.unauthorized("This account is disabled.");
        }
        String newRawToken = generateRefreshToken();
        RefreshTokenSession replacement = RefreshTokenSession.create(
                user, hash(newRawToken), session.getFamilyId(), now,
                now.plus(properties.security().refreshTokenTtl()), info.userAgent(), info.ipAddress());
        refreshTokenRepository.save(replacement);
        session.setRevokedAt(now);
        session.setReplacedBy(replacement.getId());
        refreshTokenRepository.save(session);
        JwtService.AccessToken accessToken = jwtService.issue(user);
        return new AuthResult(accessToken, newRawToken, accountService.summary(user));
    }

    @Transactional
    public AuthResult login(AuthDtos.LoginRequest request, RequestInfo info) {
        String email = UserAccountService.normalizeEmail(request.email());
        String ip = info != null ? info.ipAddress() : "";
        if (loginAttemptLimiter.isBlocked(email, ip)) {
            throw AppExceptions.tooManyRequests("TOO_MANY_ATTEMPTS",
                    "Too many failed login attempts. Please wait a few minutes.");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordOk || !user.isEnabled()) {
            loginAttemptLimiter.recordFailure(email, ip);
            throw AppExceptions.unauthorized("Email or password is incorrect.");
        }
        loginAttemptLimiter.recordSuccess(email, ip);
        return createSession(user, UUID.randomUUID(), info);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                refreshTokenRepository.save(session);
            }
        });
    }

    public AuthDtos.UserSummary me(UUID userId) {
        return accountService.summary(userId);
    }

    private AuthResult createSession(User user, UUID familyId, RequestInfo info) {
        Instant now = Instant.now();
        String rawRefreshToken = generateRefreshToken();
        RefreshTokenSession session = RefreshTokenSession.create(
                user, hash(rawRefreshToken), familyId, now,
                now.plus(properties.security().refreshTokenTtl()),
                info.userAgent(), info.ipAddress());
        refreshTokenRepository.save(session);
        JwtService.AccessToken accessToken = jwtService.issue(user);
        return new AuthResult(accessToken, rawRefreshToken, accountService.summary(user));
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void assertPassword(String password, String email) {
        if (!PasswordPolicy.isValid(password, email)) {
            throw AppExceptions.badRequest("WEAK_PASSWORD", PasswordPolicy.MESSAGE);
        }
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeParseException ex) {
            throw AppExceptions.badRequest("INVALID_TIME_ZONE", "The selected time zone is not valid.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RequestInfo(String userAgent, String ipAddress) {
    }

    public record AuthResult(JwtService.AccessToken accessToken, String refreshToken,
                             AuthDtos.UserSummary user) {
    }
}
