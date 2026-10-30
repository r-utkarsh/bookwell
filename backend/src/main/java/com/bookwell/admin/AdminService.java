package com.bookwell.admin;

import com.bookwell.auth.RefreshTokenSessionRepository;
import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentRepository;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.api.PageResponse;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.common.util.QueryUtils;
import com.bookwell.notification.Notification;
import com.bookwell.notification.NotificationRepository;
import com.bookwell.notification.NotificationStatus;
import com.bookwell.notification.NotificationType;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderProfileRepository;
import com.bookwell.provider.ProviderStatus;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final RefreshTokenSessionRepository refreshTokenRepository;
    private final Clock clock;

    public AdminService(UserRepository userRepository,
                        ProviderProfileRepository providerRepository,
                        AppointmentRepository appointmentRepository,
                        NotificationRepository notificationRepository,
                        RefreshTokenSessionRepository refreshTokenRepository,
                        Clock clock) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDtos.PlatformStats stats() {
        Instant now = Instant.now(clock);
        return new AdminDtos.PlatformStats(
                userRepository.countByRole(Role.CUSTOMER),
                providerRepository.count(),
                providerRepository.countByStatus(ProviderStatus.PENDING),
                providerRepository.countByStatus(ProviderStatus.APPROVED),
                providerRepository.countByStatus(ProviderStatus.SUSPENDED),
                appointmentRepository.count(),
                appointmentRepository.countConfirmedBetween(now, now.plus(Duration.ofDays(90))),
                notificationRepository.countByStatus(NotificationStatus.PENDING),
                notificationRepository.countByStatus(NotificationStatus.FAILED),
                notificationRepository.countByStatus(NotificationStatus.SENT)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminProviderDto> providers(int page, int size, String query,
                                                               ProviderStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<ProviderProfile> providers = providerRepository.searchAdmin(
                status, QueryUtils.escapeLike(query),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"))));
        Map<UUID, Long> serviceCounts = new HashMap<>();
        List<UUID> ids = providers.getContent().stream().map(ProviderProfile::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : providerRepository.summarizeActiveServices(ids)) {
                serviceCounts.put((UUID) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.from(providers, provider -> new AdminDtos.AdminProviderDto(
                provider.getId(), provider.getUser().getId(), provider.getUser().getFullName(),
                provider.getUser().getEmail(), provider.getDisplayName(), provider.getBusinessName(),
                provider.getCategory(), provider.getCity(), provider.getStatus(), provider.isFeatured(),
                serviceCounts.getOrDefault(provider.getId(), 0L), provider.getCreatedAt()));
    }

    @Transactional
    public AdminDtos.AdminProviderDto updateProviderStatus(UUID providerId,
                                                           AdminDtos.UpdateProviderStatusRequest request) {
        ProviderProfile provider = providerRepository.findDetailedById(providerId)
                .orElseThrow(() -> AppExceptions.notFound("Provider not found."));

        User user = provider.getUser();
        Instant now = Instant.now(clock);

        if (request.status() == ProviderStatus.SUSPENDED) {
            provider.setStatus(ProviderStatus.SUSPENDED);
            user.setEnabled(false);
            userRepository.save(user);
            refreshTokenRepository.revokeAllForUser(user.getId(), now);
        } else if (request.status() == ProviderStatus.APPROVED) {
            if (!user.isEmailVerified()) {
                throw AppExceptions.conflict("EMAIL_NOT_VERIFIED", "Provider email must be verified before approval.");
            }
            provider.setStatus(ProviderStatus.APPROVED);
            user.setEnabled(true);
            userRepository.save(user);
        } else {
            provider.setStatus(request.status());
        }

        providerRepository.save(provider);
        long serviceCount = providerRepository.summarizeActiveServices(List.of(providerId)).stream()
                .findFirst().map(row -> ((Number) row[1]).longValue()).orElse(0L);
        return new AdminDtos.AdminProviderDto(
                provider.getId(), user.getId(), user.getFullName(),
                user.getEmail(), provider.getDisplayName(), provider.getBusinessName(),
                provider.getCategory(), provider.getCity(), provider.getStatus(), provider.isFeatured(),
                serviceCount, provider.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminCustomerDto> customers(int page, int size, String query, Boolean enabled) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<User> users = userRepository.searchByRole(
                Role.CUSTOMER, QueryUtils.escapeLike(query), enabled,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"))));
        return PageResponse.from(users, user -> new AdminDtos.AdminCustomerDto(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.isEnabled(), user.getCreatedAt()));
    }

    @Transactional
    public AdminDtos.AdminCustomerDto updateCustomerStatus(UUID userId,
                                                           AdminDtos.UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .filter(candidate -> candidate.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> AppExceptions.notFound("Customer not found."));
        boolean enable = Boolean.TRUE.equals(request.enabled());
        user.setEnabled(enable);
        userRepository.save(user);
        if (!enable) {
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
        }
        return new AdminDtos.AdminCustomerDto(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.isEnabled(), user.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AdminNotificationDto> notifications(int page, int size,
                                                                      NotificationStatus status,
                                                                      NotificationType type) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Page<Notification> notifications = notificationRepository.searchAdmin(
                status, type, PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt"))));
        return PageResponse.from(notifications, this::toNotificationDto);
    }

    @Transactional
    public AdminDtos.AdminNotificationDto retryNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> AppExceptions.notFound("Notification not found."));
        if (notification.getStatus() != NotificationStatus.FAILED && notification.getStatus() != NotificationStatus.SKIPPED) {
            throw AppExceptions.conflict("NOTIFICATION_NOT_RETRYABLE",
                    "Only failed or skipped notifications can be retried.");
        }
        notification.setStatus(NotificationStatus.PENDING);
        notification.setAttempts(0);
        notification.setScheduledAt(Instant.now(clock));
        notification.setLastError(null);
        notificationRepository.save(notification);
        return toNotificationDto(notification);
    }

    private AdminDtos.AdminNotificationDto toNotificationDto(Notification notification) {
        return new AdminDtos.AdminNotificationDto(
                notification.getId(),
                notification.getAppointment().getId(),
                notification.getType(),
                notification.getRecipientEmail(),
                notification.getStatus(),
                notification.getAttempts(),
                notification.getMaxAttempts(),
                notification.getScheduledAt(),
                notification.getSentAt(),
                notification.getLastError(),
                notification.getDeliveryMode(),
                notification.getCreatedAt());
    }
}
