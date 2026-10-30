package com.bookwell.booking;

import com.bookwell.availability.AvailabilityService;
import com.bookwell.common.api.PageResponse;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.common.exception.BookingConflictException;
import com.bookwell.common.security.AuthUser;
import com.bookwell.config.AppProperties;
import com.bookwell.notification.NotificationService;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderProfileRepository;
import com.bookwell.provider.ProviderStatus;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.provider.ServiceOfferingRepository;
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BookingService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final Clock clock;

    public BookingService(AppointmentRepository appointmentRepository,
                          UserRepository userRepository,
                          ProviderProfileRepository providerRepository,
                          ServiceOfferingRepository serviceRepository,
                          AvailabilityService availabilityService,
                          NotificationService notificationService,
                          AppProperties properties,
                          Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.availabilityService = availabilityService;
        this.notificationService = notificationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public BookingDtos.AppointmentDto create(AuthUser actor, BookingDtos.CreateAppointmentRequest request) {
        if (actor.role() != Role.CUSTOMER) {
            throw AppExceptions.forbidden("Only customer accounts can create bookings.");
        }
        User customer = userRepository.findById(actor.id())
                .filter(User::isEnabled)
                .orElseThrow(() -> AppExceptions.unauthorized("Your account is unavailable."));
        ProviderProfile provider = providerRepository.findById(request.providerId())
                .orElseThrow(() -> AppExceptions.notFound("Provider not found."));
        if (provider.getStatus() != ProviderStatus.APPROVED) {
            throw AppExceptions.conflict("PROVIDER_NOT_BOOKABLE", "This provider is not currently accepting bookings.");
        }
        ServiceOffering service = serviceRepository.findByIdAndProviderId(request.serviceId(), provider.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> AppExceptions.notFound("Service not found."));
        Instant start = validateStart(request.startAt());
        Instant now = Instant.now(clock);
        if (!start.isAfter(now)) {
            throw AppExceptions.badRequest("PAST_APPOINTMENT", "Choose a time in the future.");
        }
        availabilityService.assertSlotOffered(provider, service, start);
        Instant end = start.plus(Duration.ofMinutes(service.getDurationMinutes()));
        Appointment appointment = Appointment.create(provider, customer, service, start, end,
                request.notes() == null ? null : request.notes().trim());
        saveWithConflictTranslation(appointment);
        notificationService.bookingCreated(appointment);
        return toDto(appointment, actor, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDtos.AppointmentDto> listForCustomer(AuthUser actor, String scope,
                                                                    int page, int size) {
        if (actor.role() != Role.CUSTOMER) {
            throw AppExceptions.forbidden("Only customer accounts have a personal booking history.");
        }
        String normalizedScope = scope == null ? "upcoming" : scope.toLowerCase(Locale.ROOT);
        if (!normalizedScope.equals("upcoming") && !normalizedScope.equals("history")) {
            throw AppExceptions.badRequest("INVALID_SCOPE", "Scope must be either 'upcoming' or 'history'.");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Sort sort = normalizedScope.equals("upcoming")
                ? Sort.by(Sort.Order.asc("startAt"))
                : Sort.by(Sort.Order.desc("startAt"));
        Instant now = Instant.now(clock);
        Page<Appointment> result = appointmentRepository.findForCustomer(
                actor.id(), normalizedScope, now, PageRequest.of(safePage, safeSize, sort));
        return PageResponse.from(result, appointment -> toDto(appointment, actor, now));
    }

    @Transactional(readOnly = true)
    public BookingDtos.AppointmentDto get(AuthUser actor, UUID appointmentId) {
        Appointment appointment = detailed(appointmentId);
        requireAccess(actor, appointment);
        return toDto(appointment, actor, Instant.now(clock));
    }

    @Transactional
    public BookingDtos.AppointmentDto cancel(AuthUser actor, UUID appointmentId, String reason) {
        Appointment appointment = detailed(appointmentId);
        AppointmentActor cancellationActor = requireAccess(actor, appointment);
        Instant now = Instant.now(clock);
        enforceModificationWindow(actor, appointment, now, "cancel");
        try {
            appointment.cancel(cancellationActor, reason == null || reason.isBlank() ? null : reason.trim(), now);
        } catch (IllegalStateException ex) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_CANCELLABLE", ex.getMessage());
        }
        appointmentRepository.saveAndFlush(appointment);
        notificationService.bookingCancelled(appointment);
        return toDto(appointment, actor, now);
    }

    @Transactional
    public BookingDtos.AppointmentDto reschedule(AuthUser actor, UUID appointmentId,
                                                 BookingDtos.RescheduleRequest request) {
        Appointment appointment = detailed(appointmentId);
        requireAccess(actor, appointment);
        Instant now = Instant.now(clock);
        enforceModificationWindow(actor, appointment, now, "reschedule");
        Instant newStart = validateStart(request.startAt());
        if (!newStart.isAfter(now)) {
            throw AppExceptions.badRequest("PAST_APPOINTMENT", "Choose a time in the future.");
        }
        if (newStart.equals(appointment.getStartAt())) {
            throw AppExceptions.badRequest("SAME_TIME", "That is already the appointment time.");
        }
        ProviderProfile provider = appointment.getProvider();
        ServiceOffering service = appointment.getService();
        if (provider.getStatus() != ProviderStatus.APPROVED) {
            throw AppExceptions.conflict("PROVIDER_NOT_BOOKABLE", "This provider is not currently accepting bookings.");
        }
        if (!service.isActive()) {
            throw AppExceptions.conflict("SERVICE_INACTIVE",
                    "This service is no longer offered, so it cannot be rescheduled online.");
        }
        if (service.getDurationMinutes() != appointment.getServiceDurationMinutes()) {
            throw AppExceptions.conflict("SERVICE_CHANGED",
                    "The duration of this service has changed. Please cancel this booking and book again.");
        }
        availabilityService.assertSlotOffered(provider, service, newStart);
        Instant previousStart = appointment.getStartAt();
        Instant newEnd = newStart.plus(Duration.ofMinutes(appointment.getServiceDurationMinutes()));
        try {
            appointment.reschedule(newStart, newEnd);
        } catch (IllegalStateException ex) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_RESCHEDULABLE", ex.getMessage());
        }
        saveWithConflictTranslation(appointment);
        notificationService.bookingRescheduled(appointment, previousStart);
        return toDto(appointment, actor, now);
    }

    @Transactional
    public BookingDtos.AppointmentDto markCompleted(AuthUser actor, UUID appointmentId) {
        Appointment appointment = detailed(appointmentId);
        requireProviderOrAdmin(actor, appointment);
        Instant now = Instant.now(clock);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_COMPLETABLE", "Only confirmed appointments can be completed.");
        }
        if (appointment.getEndAt().isAfter(now)) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_FINISHED", "This appointment has not finished yet.");
        }
        try {
            appointment.markCompleted();
        } catch (IllegalStateException ex) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_COMPLETABLE", ex.getMessage());
        }
        appointmentRepository.save(appointment);
        return toDto(appointment, actor, now);
    }

    @Transactional
    public BookingDtos.AppointmentDto markNoShow(AuthUser actor, UUID appointmentId) {
        Appointment appointment = detailed(appointmentId);
        requireProviderOrAdmin(actor, appointment);
        Instant now = Instant.now(clock);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_UPDATABLE", "Only confirmed appointments can be marked as no-show.");
        }
        if (appointment.getStartAt().isAfter(now)) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_STARTED", "This appointment has not started yet.");
        }
        try {
            appointment.markNoShow();
        } catch (IllegalStateException ex) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_UPDATABLE", ex.getMessage());
        }
        appointmentRepository.save(appointment);
        return toDto(appointment, actor, now);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.AppointmentDto> providerCalendar(AuthUser actor, LocalDate from, LocalDate to,
                                                              AppointmentStatus status) {
        if (actor.role() != Role.PROVIDER) {
            throw AppExceptions.forbidden("Only providers can view a provider calendar.");
        }
        ProviderProfile provider = providerRepository.findByUserId(actor.id())
                .orElseThrow(() -> AppExceptions.notFound("Provider profile not found."));
        if (provider.getStatus() == ProviderStatus.SUSPENDED) {
            throw AppExceptions.forbidden("Suspended providers cannot access calendar.");
        }
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        LocalDate effectiveFrom = from == null ? LocalDate.now(clock.withZone(zone)) : from;
        LocalDate effectiveTo = to == null ? effectiveFrom.plusDays(30) : to;
        if (effectiveTo.isBefore(effectiveFrom)) {
            throw AppExceptions.badRequest("INVALID_DATE_RANGE", "The end date must be on or after the start date.");
        }
        if (effectiveFrom.plusDays(93).isBefore(effectiveTo)) {
            throw AppExceptions.badRequest("DATE_RANGE_TOO_LARGE", "Calendar queries are limited to 93 days.");
        }
        Instant start = effectiveFrom.atStartOfDay(zone).toInstant();
        Instant end = effectiveTo.plusDays(1).atStartOfDay(zone).toInstant();
        Instant now = Instant.now(clock);
        return appointmentRepository.findForProviderInRange(provider.getId(), start, end, status).stream()
                .map(appointment -> toDto(appointment, actor, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingDtos.AppointmentDto> searchAdmin(AuthUser actor, UUID providerId, UUID customerId,
                                                                AppointmentStatus status, Instant from, Instant to,
                                                                int page, int size) {
        if (actor.role() != Role.ADMIN) {
            throw AppExceptions.forbidden("Administrator access required.");
        }
        Instant now = Instant.now(clock);
        Instant effectiveFrom = from == null ? now.minus(Duration.ofDays(30)) : from;
        Instant effectiveTo = to == null ? now.plus(Duration.ofDays(90)) : to;
        if (!effectiveTo.isAfter(effectiveFrom)) {
            throw AppExceptions.badRequest("INVALID_DATE_RANGE", "The end time must be after the start time.");
        }
        if (effectiveFrom.plus(Duration.ofDays(366)).isBefore(effectiveTo)) {
            throw AppExceptions.badRequest("DATE_RANGE_TOO_LARGE", "Administrative searches are limited to 366 days.");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Appointment> result = appointmentRepository.searchAdmin(
                providerId, customerId, status, effectiveFrom, effectiveTo,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("startAt"))));
        return PageResponse.from(result, appointment -> toDto(appointment, actor, now));
    }

    private Appointment detailed(UUID id) {
        return appointmentRepository.findDetailedById(id)
                .orElseThrow(() -> AppExceptions.notFound("Appointment not found."));
    }

    private void enforceModificationWindow(AuthUser actor, Appointment appointment, Instant now, String action) {
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw AppExceptions.conflict("APPOINTMENT_NOT_MODIFIABLE",
                    "This appointment can no longer be " + (action.equals("cancel") ? "cancelled." : "rescheduled."));
        }
        if (actor.role() == Role.ADMIN) {
            return;
        }
        if (actor.role() == Role.CUSTOMER) {
            Instant cutoff = appointment.getStartAt().minus(properties.booking().cancellationCutoff());
            if (!now.isBefore(cutoff)) {
                throw AppExceptions.conflict("CANCELLATION_CUTOFF",
                        "Online changes close " + formatCutoff(properties.booking().cancellationCutoff())
                                + " before the appointment. Please contact the provider directly.");
            }
        } else if (actor.role() == Role.PROVIDER) {
            if (!now.isBefore(appointment.getStartAt())) {
                throw AppExceptions.conflict("APPOINTMENT_STARTED",
                        "This appointment has already started.");
            }
        }
    }

    private AppointmentActor requireAccess(AuthUser actor, Appointment appointment) {
        return switch (actor.role()) {
            case ADMIN -> AppointmentActor.ADMIN;
            case CUSTOMER -> {
                if (!appointment.getCustomer().getId().equals(actor.id())) {
                    throw AppExceptions.forbidden("You can only access your own appointments.");
                }
                yield AppointmentActor.CUSTOMER;
            }
            case PROVIDER -> {
                ProviderProfile provider = providerRepository.findByUserId(actor.id())
                        .orElseThrow(() -> AppExceptions.forbidden("Provider profile not found."));
                if (!appointment.getProvider().getId().equals(provider.getId())) {
                    throw AppExceptions.forbidden("You can only access appointments for your own business.");
                }
                yield AppointmentActor.PROVIDER;
            }
        };
    }

    private void requireProviderOrAdmin(AuthUser actor, Appointment appointment) {
        if (actor.role() == Role.ADMIN) {
            return;
        }
        if (actor.role() != Role.PROVIDER) {
            throw AppExceptions.forbidden("Only the provider or an administrator can perform this action.");
        }
        ProviderProfile provider = providerRepository.findByUserId(actor.id())
                .orElseThrow(() -> AppExceptions.forbidden("Provider profile not found."));
        if (provider.getStatus() == ProviderStatus.SUSPENDED) {
            throw AppExceptions.forbidden("Suspended providers cannot manage appointments.");
        }
        if (!appointment.getProvider().getId().equals(provider.getId())) {
            throw AppExceptions.forbidden("You can only manage appointments for your own business.");
        }
    }

    private BookingDtos.AppointmentDto toDto(Appointment appointment, AuthUser actor, Instant now) {
        ProviderProfile provider = appointment.getProvider();
        ServiceOffering service = appointment.getService();
        boolean confirmed = appointment.getStatus() == AppointmentStatus.CONFIRMED;
        boolean windowOpen = switch (actor.role()) {
            case ADMIN -> true;
            case CUSTOMER -> now.isBefore(appointment.getStartAt().minus(properties.booking().cancellationCutoff()));
            case PROVIDER -> now.isBefore(appointment.getStartAt());
        };
        boolean rescheduleCapable = service.isActive()
                && provider.getStatus() == ProviderStatus.APPROVED
                && service.getDurationMinutes() == appointment.getServiceDurationMinutes();
        return new BookingDtos.AppointmentDto(
                appointment.getId(),
                appointment.getStatus(),
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getCreatedAt(),
                provider.getId(),
                provider.getDisplayName(),
                provider.getBusinessName(),
                provider.getTimeZone(),
                provider.getCity(),
                provider.getAddress(),
                appointment.getService().getId(),
                appointment.getServiceName(),
                appointment.getServiceDurationMinutes(),
                appointment.getServicePrice(),
                appointment.getCurrency(),
                appointment.getCustomer().getId(),
                appointment.getCustomerName(),
                appointment.getCustomerEmail(),
                appointment.getCustomerPhone(),
                appointment.getCustomerNotes(),
                appointment.getCancellationReason(),
                appointment.getCancelledAt(),
                appointment.getCancelledBy(),
                appointment.getRescheduledFromAt(),
                appointment.getRescheduleCount(),
                confirmed && windowOpen,
                confirmed && windowOpen && rescheduleCapable
        );
    }

    private void saveWithConflictTranslation(Appointment appointment) {
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataAccessException ex) {
            // Concurrent inserts can produce either an exclusion violation
            // (23P01) or, when both transactions check the GiST index at the
            // same time, a deadlock (40P01). Both mean this request lost the
            // race and must be retried as a slot conflict, never a 500.
            if (isExclusionViolation(ex) || hasSqlState(ex, "40P01")) {
                throw new BookingConflictException();
            }
            throw ex;
        }
    }

    private boolean isExclusionViolation(Throwable throwable) {
        if (hasSqlState(throwable, "23P01")) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains("appointments_no_overlap")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasSqlState(Throwable throwable, String sqlState) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Instant validateStart(Instant startAt) {
        if (startAt.getNano() != 0 || startAt.getEpochSecond() % 60 != 0) {
            throw AppExceptions.badRequest("INVALID_START_TIME",
                    "Start time must be an ISO-8601 instant with minute precision and an explicit offset (for example 2026-11-02T05:30:00Z).");
        }
        return startAt;
    }

    private String formatCutoff(Duration cutoff) {
        long hours = cutoff.toHours();
        if (hours >= 1 && cutoff.toMinutes() % 60 == 0) {
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return cutoff.toMinutes() + " minutes";
    }
}
