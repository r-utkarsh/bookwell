package com.bookwell.auth;

import com.bookwell.common.exception.ApiException;
import com.bookwell.config.AppProperties;
import com.bookwell.notification.MailGateway;
import com.bookwell.notification.NotificationMailComposer;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    private EmailVerificationTokenRepository tokenRepository;
    private UserRepository userRepository;
    private NotificationMailComposer mailComposer;
    private MailGateway mailGateway;
    private AppProperties properties;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(EmailVerificationTokenRepository.class);
        userRepository = mock(UserRepository.class);
        mailComposer = mock(NotificationMailComposer.class);
        mailGateway = mock(MailGateway.class);
        properties = mock(AppProperties.class, RETURNS_DEEP_STUBS);
        when(properties.notifications().appPublicUrl()).thenReturn("http://localhost:5173");

        service = new EmailVerificationService(
                tokenRepository,
                userRepository,
                mailComposer,
                mailGateway,
                properties
        );
    }

    @Test
    void issueForGeneratesTokenAndDeletesOlderUnusedTokens() {
        User user = User.create("alice@example.com", "hash", Role.CUSTOMER, "Alice", null);
        String rawToken = service.issueFor(user);

        assertThat(rawToken).isNotBlank();
        verify(tokenRepository).deleteUnusedForUser(user.getId());
        verify(tokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyMarksUserAndTokenVerified() {
        User user = User.create("alice@example.com", "hash", Role.CUSTOMER, "Alice", null);
        assertThat(user.isEmailVerified()).isFalse();

        String rawToken = "sample-token-12345678901234567890";
        String tokenHash = EmailVerificationService.hash(rawToken);
        EmailVerificationToken token = EmailVerificationToken.create(
                user, tokenHash, Instant.now().plusSeconds(3600));

        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        service.verify(rawToken);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void verifyRejectsExpiredOrUsedTokens() {
        User user = User.create("alice@example.com", "hash", Role.CUSTOMER, "Alice", null);
        String rawToken = "expired-token-12345678901234567890";
        String tokenHash = EmailVerificationService.hash(rawToken);

        // Expired token
        EmailVerificationToken expired = EmailVerificationToken.create(
                user, tokenHash, Instant.now().minusSeconds(10));
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verify(rawToken))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid, expired, or has already been used");
    }

    @Test
    void resendEnforcesPerUserCooldown() {
        User user = User.create("alice@example.com", "hash", Role.CUSTOMER, "Alice", null);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(mailGateway.send(any())).thenReturn(MailGateway.DeliveryResult.success("log"));

        service.resend(user.getId());

        // Second immediate call should throw 429
        assertThatThrownBy(() -> service.resend(user.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Please wait a minute");
    }
}
