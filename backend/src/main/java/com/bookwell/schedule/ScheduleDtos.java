package com.bookwell.schedule;

import com.bookwell.schedule.ScheduleExceptionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record WeeklyIntervalRequest(
            @NotNull(message = "Day of week is required.")
            @Min(value = 1, message = "Day must be between 1 (Monday) and 7 (Sunday).")
            @Max(value = 7, message = "Day must be between 1 (Monday) and 7 (Sunday).")
            Integer dayOfWeek,

            @NotNull(message = "Start time is required.")
            LocalTime startTime,

            @NotNull(message = "End time is required.")
            LocalTime endTime
    ) {
    }

    public record ReplaceWeeklyScheduleRequest(
            @NotNull(message = "Weekly hours are required.")
            @Size(max = 35, message = "Too many working intervals.")
            List<WeeklyIntervalRequest> intervals
    ) {
    }

    public record WeeklyIntervalDto(
            UUID id,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime
    ) {
    }

    public record CreateExceptionRequest(
            @NotNull(message = "Date is required.")
            LocalDate date,

            @NotNull(message = "Exception type is required.")
            ScheduleExceptionType type,

            LocalTime startTime,

            LocalTime endTime,

            @Size(max = 240, message = "Reason is too long.")
            String reason
    ) {
    }

    public record ExceptionDto(
            UUID id,
            LocalDate date,
            ScheduleExceptionType type,
            LocalTime startTime,
            LocalTime endTime,
            String reason
    ) {
    }

    public record ScheduleOverview(
            List<WeeklyIntervalDto> weeklyHours,
            List<ExceptionDto> upcomingExceptions,
            String timeZone
    ) {
    }
}
