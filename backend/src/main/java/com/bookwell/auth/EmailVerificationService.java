package com.bookwell.auth;

import com.bookwell.common.exception.AppExceptions;
import com.bookwell.config.AppProperties;
import com.bookwell.notification.MailGateway;
import com.bookwell.notification.NotificationMailComposer;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final long RESEND_COOLDOWN_MS = 60_000L;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationMailComposer mailComposer;
    private final MailGateway mailGateway;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<UUID, Long> resendTimestamps = new ConcurrentHashMap<>();

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    NotificationMailComposer mailComposer,
                                    MailGateway mailGateway,
                                    AppProperties properties) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.mailComposer = mailComposer;
        this.mailGateway = mailGateway;
        this.properties = properties;
    }

    @Transactional
    public String issueFor(User user) {
        tokenRepository.deleteUnusedForUser(user.getId());
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(rawToken);
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        EmailVerificationToken token = EmailVerificationToken.create(user, tokenHash, expiresAt);
        tokenRepository.save(token);
        return rawToken;
    }

    @Async
    public void sendVerificationEmailAsync(User user, String rawToken) {
        try {
            String appUrl = properties.notifications().appPublicUrl();
            if (appUrl == null || appUrl.isBlank()) {
                appUrl = "http://localhost:5173";
            }
            if (appUrl.endsWith("/")) {
                appUrl = appUrl.substring(0, appUrl.length() - 1);
            }
            String link = appUrl + "/verify-email#token=" + rawToken;
            NotificationMailComposer.EmailMessage message = mailComposer.composeVerification(
                    user.getEmail(), user.getFullName(), link);
            MailGateway.DeliveryResult result = mailGateway.send(message);
            if (!result.success()) {
                log.warn("Failed to deliver verification email to {}: {}", user.getEmail(), result.error());
            }
        } catch (Exception ex) {
            log.error("Error while dispatching verification email to {}", user.getEmail(), ex);
        }
    }

    @Transactional
    public void verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AppExceptions.badRequest("INVALID_TOKEN",
                    "The verification link is invalid, expired, or has already been used.");
        }
        String tokenHash = hash(rawToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> AppExceptions.badRequest("INVALID_TOKEN",
                        "The verification link is invalid, expired, or has already been used."));

        Instant now = Instant.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw AppExceptions.badRequest("INVALID_TOKEN",
                    "The verification link is invalid, expired, or has already been used.");
        }

        token.setUsedAt(now);
        tokenRepository.save(token);

        User user = token.getUser();
        user.markEmailVerified(now);
        userRepository.save(user);
    }

    @Transactional
    public void resend(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppExceptions.notFound("User not found."));

        if (user.isEmailVerified()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = resendTimestamps.get(userId);
        if (last != null && now - last < RESEND_COOLDOWN_MS) {
            throw AppExceptions.tooManyRequests("TOO_MANY_REQUESTS",
                    "Please wait a minute before requesting another verification email.");
        }
        resendTimestamps.put(userId, now);

        String rawToken = issueFor(user);
        sendVerificationEmailAsync(user, rawToken);
    }

    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
