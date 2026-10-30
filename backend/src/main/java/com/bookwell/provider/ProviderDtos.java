package com.bookwell.provider;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ProviderDtos {

    private ProviderDtos() {
    }

    public record ProviderCard(
            UUID id,
            String displayName,
            String businessName,
            String category,
            String description,
            String city,
            String address,
            String phone,
            String timeZone,
            String currency,
            boolean featured,
            ProviderStatus status,
            long serviceCount,
            BigDecimal startingPrice
    ) {
    }

    public record ProviderDetail(
            ProviderCard provider,
            List<ServiceDto> services,
            List<WeeklyHourDto> weeklyHours
    ) {
    }

    public record ServiceDto(
            UUID id,
            String name,
            String description,
            int durationMinutes,
            BigDecimal price,
            String currency,
            boolean active,
            int sortOrder
    ) {
    }

    public record WeeklyHourDto(
            UUID id,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }

    public record ProviderDashboardProfile(
            UUID id,
            UUID userId,
            String ownerName,
            String ownerEmail,
            String displayName,
            String businessName,
            String category,
            String description,
            String city,
            String address,
            String phone,
            String timeZone,
            String currency,
            ProviderStatus status,
            boolean featured,
            Instant createdAt
    ) {
    }

    public record UpdateProviderProfileRequest(
            @NotBlank(message = "Display name is required.")
            @Size(min = 2, max = 140, message = "Display name must be between 2 and 140 characters.")
            String displayName,

            @NotBlank(message = "Business name is required.")
            @Size(min = 2, max = 160, message = "Business name must be between 2 and 160 characters.")
            String businessName,

            @NotBlank(message = "Category is required.")
            @Size(max = 60, message = "Category is too long.")
            String category,

            @Size(max = 4000, message = "Description is too long.")
            String description,

            @Size(max = 100, message = "City is too long.")
            String city,

            @Size(max = 240, message = "Address is too long.")
            String address,

            @Size(max = 30, message = "Phone number is too long.")
            String phone,

            @NotBlank(message = "Time zone is required.")
            @Size(max = 64, message = "Time zone is too long.")
            String timeZone
    ) {
    }

    public record ServiceRequest(
            @NotBlank(message = "Service name is required.")
            @Size(min = 2, max = 140, message = "Service name must be between 2 and 140 characters.")
            String name,

            @Size(max = 4000, message = "Description is too long.")
            String description,

            @NotNull(message = "Duration is required.")
            @Min(value = 5, message = "Duration must be at least 5 minutes.")
            @Max(value = 720, message = "Duration cannot exceed 720 minutes.")
            Integer durationMinutes,

            @NotNull(message = "Price is required.")
            @DecimalMin(value = "0.00", message = "Price cannot be negative.")
            @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimal places.")
            BigDecimal price,

            @NotNull(message = "Active status is required.")
            Boolean active,

            @Min(value = 0, message = "Sort order cannot be negative.")
            @Max(value = 1000, message = "Sort order cannot exceed 1000.")
            Integer sortOrder
    ) {
    }

    public record ProviderPublicPage(
            List<ProviderCard> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }

    public record ProviderDashboardStats(
            long upcomingAppointments,
            long todaysAppointments,
            long activeServices,
            long completedAppointments
    ) {
    }
}
