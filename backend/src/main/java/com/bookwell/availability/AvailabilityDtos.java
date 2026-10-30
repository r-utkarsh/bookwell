package com.bookwell.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {

    private AvailabilityDtos() {
    }

    public record AvailabilitySlot(
            Instant startAt,
            Instant endAt,
            String label
    ) {
    }

    public record AvailabilityResponse(
            UUID providerId,
            UUID serviceId,
            String serviceName,
            LocalDate date,
            String timeZone,
            int durationMinutes,
            BigDecimal price,
            String currency,
            List<AvailabilitySlot> slots,
            String message
    ) {
    }
}
