package com.bookwell.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateAppointmentRequest(
            @NotNull(message = "Provider is required.")
            UUID providerId,

            @NotNull(message = "Service is required.")
            UUID serviceId,

            @NotNull(message = "Start time is required.")
            Instant startAt,

            @Size(max = 1000, message = "Notes cannot exceed 1000 characters.")
            String notes
    ) {
    }

    public record RescheduleRequest(
            @NotNull(message = "New start time is required.")
            Instant startAt
    ) {
    }

    public record CancelRequest(
            @Size(max = 500, message = "Cancellation reason cannot exceed 500 characters.")
            String reason
    ) {
    }

    public record AppointmentDto(
            UUID id,
            AppointmentStatus status,
            Instant startAt,
            Instant endAt,
            Instant createdAt,
            UUID providerId,
            String providerDisplayName,
            String providerBusinessName,
            String providerTimeZone,
            String providerCity,
            String providerAddress,
            UUID serviceId,
            String serviceName,
            int serviceDurationMinutes,
            BigDecimal servicePrice,
            String currency,
            UUID customerId,
            String customerName,
            String customerEmail,
            String customerPhone,
            String customerNotes,
            String cancellationReason,
            Instant cancelledAt,
            AppointmentActor cancelledBy,
            Instant rescheduledFromAt,
            int rescheduleCount,
            boolean canCancel,
            boolean canReschedule
    ) {
    }
}
