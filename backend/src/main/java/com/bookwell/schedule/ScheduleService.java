package com.bookwell.schedule;

import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentRepository;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.provider.ProviderManagementService;
import com.bookwell.provider.ProviderProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ScheduleService {

    private final ProviderManagementService providerManagementService;
    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    public ScheduleService(ProviderManagementService providerManagementService,
                           WeeklyScheduleRepository weeklyScheduleRepository,
                           ScheduleExceptionRepository exceptionRepository,
                           AppointmentRepository appointmentRepository,
                           Clock clock) {
        this.providerManagementService = providerManagementService;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScheduleDtos.ScheduleOverview overview(UUID userId, LocalDate from) {
        ProviderProfile provider = providerManagementService.currentProvider(userId);
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        LocalDate effectiveFrom = from == null ? LocalDate.now(clock.withZone(zone)) : from;
        List<ScheduleDtos.WeeklyIntervalDto> weekly = weeklyScheduleRepository
                .findByProviderIdOrderByDayOfWeekAscStartTimeAsc(provider.getId()).stream()
                .map(s -> new ScheduleDtos.WeeklyIntervalDto(s.getId(), s.getDayOfWeek(),
                        s.getStartTime(), s.getEndTime()))
                .toList();
        List<ScheduleDtos.ExceptionDto> exceptions = exceptionRepository
                .findByProviderIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(provider.getId(), effectiveFrom)
                .stream()
                .map(e -> new ScheduleDtos.ExceptionDto(e.getId(), e.getDate(), e.getType(),
                        e.getStartTime(), e.getEndTime(), e.getReason()))
                .toList();
        return new ScheduleDtos.ScheduleOverview(weekly, exceptions, provider.getTimeZone());
    }

    @Transactional
    public List<ScheduleDtos.WeeklyIntervalDto> replaceWeekly(UUID userId,
                                                              ScheduleDtos.ReplaceWeeklyScheduleRequest request) {
        ProviderProfile provider = providerManagementService.currentProvider(userId);
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        List<Interval> intervals = validateIntervals(request.intervals());
        assertFutureAppointmentsStillFit(provider, zone, intervals);
        weeklyScheduleRepository.deleteByProviderId(provider.getId());
        weeklyScheduleRepository.flush();
        List<WeeklySchedule> saved = new ArrayList<>();
        for (Interval interval : intervals) {
            saved.add(weeklyScheduleRepository.save(WeeklySchedule.create(
                    provider, interval.dayOfWeek(), interval.startTime(), interval.endTime())));
        }
        return saved.stream()
                .sorted(Comparator.comparingInt(WeeklySchedule::getDayOfWeek)
                        .thenComparing(WeeklySchedule::getStartTime))
                .map(s -> new ScheduleDtos.WeeklyIntervalDto(s.getId(), s.getDayOfWeek(),
                        s.getStartTime(), s.getEndTime()))
                .toList();
    }

    @Transactional
    public ScheduleDtos.ExceptionDto createException(UUID userId,
                                                     ScheduleDtos.CreateExceptionRequest request) {
        ProviderProfile provider = providerManagementService.currentProvider(userId);
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (request.date().isBefore(today)) {
            throw AppExceptions.badRequest("PAST_DATE", "Exceptions can only be added for today or future dates.");
        }
        if (request.date().isAfter(today.plusDays(365))) {
            throw AppExceptions.badRequest("DATE_TOO_FAR", "Exceptions can be scheduled at most one year ahead.");
        }
        validateExceptionShape(request);
        List<ScheduleException> sameDay = exceptionRepository
                .findByProviderIdAndDateOrderByStartTimeAsc(provider.getId(), request.date());
        boolean closedExists = sameDay.stream().anyMatch(e -> e.getType() == ScheduleExceptionType.CLOSED);
        if (request.type() == ScheduleExceptionType.CLOSED && !sameDay.isEmpty()) {
            throw AppExceptions.conflict("EXCEPTION_EXISTS",
                    "This date already has an exception. Remove it before adding a full-day closure.");
        }
        if (request.type() != ScheduleExceptionType.CLOSED && closedExists) {
            throw AppExceptions.conflict("DATE_CLOSED", "This date is already marked as closed.");
        }

        if (request.type() == ScheduleExceptionType.CLOSED) {
            Instant dayStart = request.date().atStartOfDay(zone).toInstant();
            Instant dayEnd = request.date().plusDays(1).atStartOfDay(zone).toInstant();
            assertNoConfirmedAppointments(provider.getId(), dayStart, dayEnd, "close this date");
        } else if (request.type() == ScheduleExceptionType.BLOCKED) {
            Instant start = request.date().atTime(request.startTime()).atZone(zone).toInstant();
            Instant end = request.date().atTime(request.endTime()).atZone(zone).toInstant();
            assertNoConfirmedAppointments(provider.getId(), start, end, "block this period");
        }

        ScheduleException exception = ScheduleException.create(
                provider, request.date(), request.type(), request.startTime(), request.endTime(),
                request.reason() == null ? null : request.reason().trim());
        exceptionRepository.saveAndFlush(exception);
        return new ScheduleDtos.ExceptionDto(exception.getId(), exception.getDate(), exception.getType(),
                exception.getStartTime(), exception.getEndTime(), exception.getReason());
    }

    @Transactional
    public void deleteException(UUID userId, UUID exceptionId) {
        ProviderProfile provider = providerManagementService.currentProvider(userId);
        ScheduleException exception = exceptionRepository.findByIdAndProviderId(exceptionId, provider.getId())
                .orElseThrow(() -> AppExceptions.notFound("Schedule exception not found."));
        exceptionRepository.delete(exception);
    }

    private List<Interval> validateIntervals(List<ScheduleDtos.WeeklyIntervalRequest> requests) {
        List<Interval> intervals = requests.stream()
                .map(r -> new Interval(r.dayOfWeek(), r.startTime(), r.endTime()))
                .sorted(Comparator.comparingInt(Interval::dayOfWeek).thenComparing(Interval::startTime))
                .toList();
        Map<Integer, LocalTime> lastEndByDay = new HashMap<>();
        for (Interval interval : intervals) {
            if (interval.startTime() == null || interval.endTime() == null) {
                throw AppExceptions.badRequest("INVALID_INTERVAL", "Start and end times are required.");
            }
            if (!interval.startTime().isBefore(interval.endTime())) {
                throw AppExceptions.badRequest("INVALID_INTERVAL", "Working interval start time must be before its end time.");
            }
            LocalTime previousEnd = lastEndByDay.get(interval.dayOfWeek());
            if (previousEnd != null && interval.startTime().isBefore(previousEnd)) {
                throw AppExceptions.badRequest("OVERLAPPING_INTERVALS",
                        "Working intervals on the same day cannot overlap.");
            }
            lastEndByDay.put(interval.dayOfWeek(), interval.endTime());
        }
        return intervals;
    }

    private void assertFutureAppointmentsStillFit(ProviderProfile provider, ZoneId zone, List<Interval> intervals) {
        List<Appointment> upcoming = appointmentRepository.findConfirmedFuture(provider.getId(), Instant.now(clock));
        for (Appointment appointment : upcoming) {
            ZonedDateTime localStart = appointment.getStartAt().atZone(zone);
            ZonedDateTime localEnd = appointment.getEndAt().atZone(zone);
            boolean fits = localStart.toLocalDate().equals(localEnd.toLocalDate())
                    && intervals.stream().anyMatch(interval ->
                    interval.dayOfWeek() == localStart.getDayOfWeek().getValue()
                            && !localStart.toLocalTime().isBefore(interval.startTime())
                            && !localEnd.toLocalTime().isAfter(interval.endTime()));
            if (!fits) {
                throw AppExceptions.conflict("APPOINTMENT_OUTSIDE_SCHEDULE",
                        "These hours would leave an existing appointment outside your schedule. "
                                + "Reschedule or cancel upcoming bookings first.");
            }
        }
    }

    private void validateExceptionShape(ScheduleDtos.CreateExceptionRequest request) {
        if (request.type() == ScheduleExceptionType.CLOSED) {
            if (request.startTime() != null || request.endTime() != null) {
                throw AppExceptions.badRequest("INVALID_EXCEPTION", "A full-day closure cannot have start or end times.");
            }
            return;
        }
        if (request.startTime() == null || request.endTime() == null) {
            throw AppExceptions.badRequest("INVALID_EXCEPTION", "Start and end times are required for this exception type.");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw AppExceptions.badRequest("INVALID_EXCEPTION", "Exception start time must be before its end time.");
        }
    }

    private void assertNoConfirmedAppointments(UUID providerId, Instant start, Instant end, String action) {
        List<Appointment> overlapping = appointmentRepository.findOverlapping(
                providerId, EnumSet.of(AppointmentStatus.CONFIRMED), start, end);
        if (!overlapping.isEmpty()) {
            throw AppExceptions.conflict("APPOINTMENTS_EXIST",
                    "You cannot " + action + " because " + overlapping.size()
                            + " confirmed appointment" + (overlapping.size() == 1 ? " is" : "s are")
                            + " scheduled then. Reschedule or cancel those appointments first.");
        }
    }

    private record Interval(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
    }
}
