package com.bookwell.provider;

import com.bookwell.booking.AppointmentRepository;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.exception.AppExceptions;
import com.bookwell.config.AppProperties;
import com.bookwell.user.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderManagementService {

    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppProperties properties;
    private final Clock clock;

    public ProviderManagementService(ProviderProfileRepository providerRepository,
                                     ServiceOfferingRepository serviceRepository,
                                     AppointmentRepository appointmentRepository,
                                     AppProperties properties,
                                     Clock clock) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProviderProfile currentProvider(UUID userId) {
        ProviderProfile provider = providerRepository.findDetailedByUserId(userId)
                .orElseThrow(() -> AppExceptions.notFound("Provider profile not found."));
        if (provider.getStatus() == ProviderStatus.SUSPENDED) {
            throw AppExceptions.forbidden("This provider account is suspended.");
        }
        return provider;
    }

    @Transactional(readOnly = true)
    public ProviderDtos.ProviderDashboardProfile profile(UUID userId) {
        return ProviderMapper.toDashboardProfile(currentProvider(userId));
    }

    @Transactional
    public ProviderDtos.ProviderDashboardProfile updateProfile(UUID userId,
                                                               ProviderDtos.UpdateProviderProfileRequest request) {
        ProviderProfile provider = currentProvider(userId);
        String newTimeZone = request.timeZone().trim();
        validateTimeZone(newTimeZone);
        if (!newTimeZone.equals(provider.getTimeZone())) {
            boolean hasUpcoming = !appointmentRepository.findConfirmedFuture(provider.getId(), Instant.now(clock)).isEmpty();
            if (hasUpcoming) {
                throw AppExceptions.conflict("UPCOMING_APPOINTMENTS",
                        "You cannot change time zone while you have upcoming confirmed appointments. "
                                + "Wait until your calendar is clear, then try again.");
            }
            provider.setTimeZone(newTimeZone);
        }
        provider.setDisplayName(request.displayName().trim());
        provider.setBusinessName(request.businessName().trim());
        provider.setCategory(request.category().trim());
        provider.setDescription(request.description() == null ? "" : request.description().trim());
        provider.setCity(request.city() == null ? "" : request.city().trim());
        provider.setAddress(request.address() == null ? "" : request.address().trim());
        provider.setPhone(blankToNull(request.phone()));
        providerRepository.save(provider);
        return ProviderMapper.toDashboardProfile(provider);
    }

    @Transactional(readOnly = true)
    public List<ProviderDtos.ServiceDto> services(UUID userId) {
        ProviderProfile provider = currentProvider(userId);
        return serviceRepository.findByProviderIdOrderBySortOrderAscNameAsc(provider.getId()).stream()
                .map(ProviderMapper::toService)
                .toList();
    }

    @Transactional
    public ProviderDtos.ServiceDto createService(UUID userId, ProviderDtos.ServiceRequest request) {
        ProviderProfile provider = currentProvider(userId);
        ServiceOffering service = ServiceOffering.create(provider);
        apply(service, request);
        serviceRepository.save(service);
        return ProviderMapper.toService(service);
    }

    @Transactional
    public ProviderDtos.ServiceDto updateService(UUID userId, UUID serviceId, ProviderDtos.ServiceRequest request) {
        ProviderProfile provider = currentProvider(userId);
        ServiceOffering service = serviceRepository.findByIdAndProviderId(serviceId, provider.getId())
                .orElseThrow(() -> AppExceptions.notFound("Service not found."));
        apply(service, request);
        serviceRepository.save(service);
        return ProviderMapper.toService(service);
    }

    @Transactional
    public void deactivateService(UUID userId, UUID serviceId) {
        ProviderProfile provider = currentProvider(userId);
        ServiceOffering service = serviceRepository.findByIdAndProviderId(serviceId, provider.getId())
                .orElseThrow(() -> AppExceptions.notFound("Service not found."));
        service.setActive(false);
        serviceRepository.save(service);
    }

    @Transactional(readOnly = true)
    public ProviderDtos.ProviderDashboardStats stats(UUID userId) {
        ProviderProfile provider = currentProvider(userId);
        ZoneId zone = ZoneId.of(provider.getTimeZone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        long upcoming = appointmentRepository.findConfirmedFuture(provider.getId(), Instant.now(clock)).size();
        long todays = appointmentRepository.countConfirmedForProviderBetween(provider.getId(), dayStart, dayEnd);
        long activeServices = serviceRepository.countByProviderIdAndActiveTrue(provider.getId());
        long completed = appointmentRepository.countByProviderIdAndStatus(provider.getId(), AppointmentStatus.COMPLETED);
        return new ProviderDtos.ProviderDashboardStats(upcoming, todays, activeServices, completed);
    }

    private void apply(ServiceOffering service, ProviderDtos.ServiceRequest request) {
        service.setName(request.name().trim());
        service.setDescription(request.description() == null ? "" : request.description().trim());
        service.setDurationMinutes(request.durationMinutes());
        service.setPrice(request.price().setScale(2, java.math.RoundingMode.UNNECESSARY));
        service.setActive(Boolean.TRUE.equals(request.active()));
        service.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private void validateTimeZone(String timeZone) {
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeParseException ex) {
            throw AppExceptions.badRequest("INVALID_TIME_ZONE", "The selected time zone is not valid.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
