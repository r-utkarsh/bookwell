package com.bookwell.admin;

import com.bookwell.booking.AppointmentStatus;
import com.bookwell.notification.NotificationStatus;
import com.bookwell.notification.NotificationType;
import com.bookwell.provider.ProviderStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record PlatformStats(
            long totalCustomers,
            long totalProviders,
            long pendingProviders,
            long approvedProviders,
            long suspendedProviders,
            long totalBookings,
            long upcomingConfirmedBookings,
            long pendingNotifications,
            long failedNotifications,
            long sentNotifications
    ) {
    }

    public record AdminProviderDto(
            UUID id,
            UUID ownerId,
            String ownerName,
            String ownerEmail,
            String displayName,
            String businessName,
            String category,
            String city,
            ProviderStatus status,
            boolean featured,
            long serviceCount,
            Instant createdAt
    ) {
    }

    public record UpdateProviderStatusRequest(
            @NotNull(message = "Status is required.")
            ProviderStatus status
    ) {
    }

    public record AdminCustomerDto(
            UUID id,
            String email,
            String fullName,
            String phone,
            boolean enabled,
            Instant createdAt
    ) {
    }

    public record UpdateUserStatusRequest(
            @NotNull(message = "Enabled is required.")
            Boolean enabled
    ) {
    }

    public record AdminNotificationDto(
            UUID id,
            UUID appointmentId,
            NotificationType type,
            String recipientEmail,
            NotificationStatus status,
            int attempts,
            int maxAttempts,
            Instant scheduledAt,
            Instant sentAt,
            String lastError,
            String deliveryMode,
            Instant createdAt
    ) {
    }

    public record AdminBookingFilter(
            UUID providerId,
            UUID customerId,
            AppointmentStatus status,
            Instant from,
            Instant to
    ) {
    }
}
