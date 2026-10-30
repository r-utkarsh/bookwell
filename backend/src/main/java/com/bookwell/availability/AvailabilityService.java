package com.bookwell.availability;

import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentRepository;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.config.AppProperties;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderProfileRepository;
import com.bookwell.provider.ProviderStatus;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.provider.ServiceOfferingRepository;
import com.bookwell.schedule.ScheduleException;
import com.bookwell.schedule.ScheduleExceptionRepository;
import com.bookwell.schedule.ScheduleExceptionType;
import com.bookwell.schedule.WeeklySchedule;
import com.bookwell.schedule.WeeklyScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AvailabilityService {

    private static final DateTimeFormatter SLOT_LABEL =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final EnumSet<AppointmentStatus> BLOCKING_STATUSES =
            EnumSet.of(AppointmentStatus.CONFIRMED);

    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppProperties properties;
    private final Clock clock;

    public AvailabilityService(ProviderProfileRepository providerRepository,
                               ServiceOfferingRepository serviceRepository,
                               WeeklyScheduleRepository weeklyScheduleRepository,
                               ScheduleExceptionRepository exceptionRepository,
                               AppointmentRepository appointmentRepository,
                               AppProperties properties,
                               Clock clock) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityDtos.AvailabilityResponse get(UUID providerId, UUID serviceId, LocalDate date) {
        ProviderProfile provider = providerRepository.findById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(() -> AppExceptions.notFound("Provider not found."));
        ServiceOffering service = serviceRepository.findByIdAndProviderId(serviceId, providerId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> AppExceptions.notFound("Service not found."));
        Calculation calculation = calculate(provider, service, date, Instant.now(clock));
        List<AvailabilityDtos.AvailabilitySlot> slots = calculation.slots().stream()
                .map(slot -> new AvailabilityDtos.AvailabilitySlot(
                        slot.start(), slot.end(), slot.label()))
                .toList();
        return new AvailabilityDtos.AvailabilityResponse(
                provider.getId(), service.getId(), service.getName(), date, provider.getTimeZone(),
                service.getDurationMinutes(), service.getPrice(), service.getCurrency(),
                slots, calculation.message());
    }

    @Transactional(readOnly = true)
    public void assertSlotOffered(ProviderProfile provider, ServiceOffering service, Instant startAt) {
        LocalDate localDate = startAt.atZone(ZoneId.of(provider.getTimeZone())).toLocalDate();
        Calculation calculation = calculate(provider, service, localDate, Instant.now(clock));
        boolean offered = calculation.slots().stream().anyMatch(slot -> slot.start().equals(startAt));
        if (!offered) {
            throw new com.bookwell.common.exception.BookingConflictException();
        }
    }

    @Transactional(readOnly = true)
    public Calculation calculate(ProviderProfile provider, ServiceOffering service, LocalDate date, Instant now) {
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        int maxAdvance = properties.booking().maxAdvanceDays();
        if (date.isBefore(today)) {
            return Calculation.empty("Choose today or a future date.");
        }
        if (date.isAfter(today.plusDays(maxAdvance))) {
            return Calculation.empty("Bookings are open up to " + maxAdvance + " days ahead.");
        }

        List<WeeklySchedule> weekly = weeklyScheduleRepository
                .findByProviderIdOrderByDayOfWeekAscStartTimeAsc(provider.getId());
        List<ScheduleException> exceptions = exceptionRepository
                .findByProviderIdAndDateOrderByStartTimeAsc(provider.getId(), date);
        boolean closed = exceptions.stream().anyMatch(e -> e.getType() == ScheduleExceptionType.CLOSED);
        if (closed) {
            return Calculation.empty("The provider is closed on this date.");
        }

        List<TimeRange> available = new ArrayList<>();
        int dayOfWeek = date.getDayOfWeek().getValue();
        weekly.stream()
                .filter(schedule -> schedule.getDayOfWeek() == dayOfWeek)
                .forEach(schedule -> addLocalRange(available, date, schedule.getStartTime(),
                        schedule.getEndTime(), zone));
        exceptions.stream()
                .filter(exception -> exception.getType() == ScheduleExceptionType.EXTRA_AVAILABILITY)
                .forEach(exception -> addLocalRange(available, date, exception.getStartTime(),
                        exception.getEndTime(), zone));

        List<TimeRange> blocked = new ArrayList<>();
        exceptions.stream()
                .filter(exception -> exception.getType() == ScheduleExceptionType.BLOCKED)
                .forEach(exception -> addLocalRange(blocked, date, exception.getStartTime(),
                        exception.getEndTime(), zone));

        List<TimeRange> windows = subtract(merge(available), merge(blocked));
        if (windows.isEmpty()) {
            return Calculation.empty("No working hours are configured for this date.");
        }

        Instant earliest = now.plus(properties.booking().minLeadTime());
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> appointments = appointmentRepository.findOverlapping(
                provider.getId(), BLOCKING_STATUSES, dayStart, dayEnd);
        Duration duration = Duration.ofMinutes(service.getDurationMinutes());
        Duration step = Duration.ofMinutes(properties.booking().startIntervalMinutes());
        List<Slot> slots = new ArrayList<>();

        for (TimeRange window : windows) {
            Instant candidate = window.start();
            while (!candidate.plus(duration).isAfter(window.end())) {
                Instant slotStart = candidate;
                Instant slotEnd = slotStart.plus(duration);
                if (!slotStart.isBefore(earliest) && appointments.stream()
                        .noneMatch(appointment -> appointment.getStartAt().isBefore(slotEnd)
                                && appointment.getEndAt().isAfter(slotStart))) {
                    String label = LocalDateTime.ofInstant(slotStart, zone).format(SLOT_LABEL);
                    slots.add(new Slot(slotStart, slotEnd, label));
                }
                candidate = candidate.plus(step);
            }
        }
        slots.sort(Comparator.comparing(Slot::start));
        if (slots.isEmpty()) {
            if (date.equals(today)) {
                return Calculation.empty("No times remain today. Try another date.");
            }
            return Calculation.empty("No available times on this date.");
        }
        return new Calculation(List.copyOf(slots), null);
    }

    private void addLocalRange(List<TimeRange> ranges, LocalDate date, LocalTime start, LocalTime end, ZoneId zone) {
        if (start == null || end == null || !start.isBefore(end)) {
            return;
        }
        Instant startInstant = date.atTime(start).atZone(zone).toInstant();
        Instant endInstant = date.atTime(end).atZone(zone).toInstant();
        if (endInstant.isAfter(startInstant)) {
            ranges.add(new TimeRange(startInstant, endInstant));
        }
    }

    private List<TimeRange> merge(List<TimeRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }
        List<TimeRange> sorted = ranges.stream()
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();
        List<TimeRange> merged = new ArrayList<>();
        TimeRange current = sorted.getFirst();
        for (int i = 1; i < sorted.size(); i++) {
            TimeRange next = sorted.get(i);
            if (!next.start().isAfter(current.end())) {
                current = new TimeRange(current.start(), next.end().isAfter(current.end()) ? next.end() : current.end());
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<TimeRange> subtract(List<TimeRange> windows, List<TimeRange> blocks) {
        List<TimeRange> result = new ArrayList<>(windows);
        for (TimeRange block : blocks) {
            List<TimeRange> next = new ArrayList<>();
            for (TimeRange window : result) {
                if (!overlaps(window, block)) {
                    next.add(window);
                    continue;
                }
                if (window.start().isBefore(block.start())) {
                    next.add(new TimeRange(window.start(), block.start()));
                }
                if (window.end().isAfter(block.end())) {
                    next.add(new TimeRange(block.end(), window.end()));
                }
            }
            result = next;
        }
        return result.stream()
                .filter(range -> range.start().isBefore(range.end()))
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();
    }

    private boolean overlaps(TimeRange a, TimeRange b) {
        return a.start().isBefore(b.end()) && a.end().isAfter(b.start());
    }

    public record Slot(Instant start, Instant end, String label) {
    }

    public record Calculation(List<Slot> slots, String message) {
        static Calculation empty(String message) {
            return new Calculation(List.of(), message);
        }
    }

    private record TimeRange(Instant start, Instant end) {
    }
}
