package com.bookwell.seed;

import com.bookwell.auth.PasswordPolicy;
import com.bookwell.auth.UserAccountService;
import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentActor;
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
import com.bookwell.user.Role;
import com.bookwell.user.User;
import com.bookwell.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final ProviderProfileRepository providerRepository;
    private final ServiceOfferingRepository serviceRepository;
    private final WeeklyScheduleRepository weeklyScheduleRepository;
    private final ScheduleExceptionRepository exceptionRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(AppProperties properties,
                          UserRepository userRepository,
                          ProviderProfileRepository providerRepository,
                          ServiceOfferingRepository serviceRepository,
                          WeeklyScheduleRepository weeklyScheduleRepository,
                          ScheduleExceptionRepository exceptionRepository,
                          AppointmentRepository appointmentRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.weeklyScheduleRepository = weeklyScheduleRepository;
        this.exceptionRepository = exceptionRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seed().enabled()) {
            return;
        }
        if (properties.isProduction()) {
            throw new IllegalStateException("Demo data seeding is disabled in production.");
        }
        AppProperties.Seed seed = properties.seed();
        validateSeedConfiguration(seed);

        String adminEmail = UserAccountService.normalizeEmail(seed.adminEmail());
        String providerEmail = UserAccountService.normalizeEmail(seed.providerEmail());
        String customerEmail = UserAccountService.normalizeEmail(seed.customerEmail());
        if (adminEmail.equals(providerEmail) || adminEmail.equals(customerEmail) || providerEmail.equals(customerEmail)) {
            throw new IllegalStateException("Seed admin, provider, and customer must use three distinct email addresses.");
        }

        User admin = createUserIfMissing(adminEmail, seed.adminPassword(), Role.ADMIN,
                seed.adminName() == null || seed.adminName().isBlank() ? "Platform Administrator" : seed.adminName(), null);
        if (admin.getRole() != Role.ADMIN) {
            throw new IllegalStateException("SEED_ADMIN_EMAIL belongs to a non-admin account.");
        }

        User providerUser = createUserIfMissing(providerEmail, seed.providerPassword(), Role.PROVIDER,
                "Aarohi Mehta", "+91 98765 43210");
        if (providerUser.getRole() != Role.PROVIDER) {
            throw new IllegalStateException("SEED_PROVIDER_EMAIL belongs to an account that is not a provider.");
        }
        User customer = createUserIfMissing(customerEmail, seed.customerPassword(), Role.CUSTOMER,
                "Riya Sharma", "+91 91234 56780");
        if (customer.getRole() != Role.CUSTOMER) {
            throw new IllegalStateException("SEED_CUSTOMER_EMAIL belongs to an account that is not a customer.");
        }

        ProviderProfile provider = providerRepository.findByUserId(providerUser.getId()).orElseGet(() -> {
            ProviderProfile profile = ProviderProfile.create(providerUser,
                    "Aarohi Mehta", "Aarohi Studio", "Salon & Spa", "Bengaluru",
                    properties.booking().defaultTimeZone(), properties.booking().defaultCurrency());
            profile.setDescription("A calm, light-filled studio in Indiranagar offering precision cuts, "
                    + "colour, and skin rituals. Every appointment is one-to-one, unhurried, and tailored to you.");
            profile.setCity("Bengaluru");
            profile.setAddress("12th Main, Indiranagar, Bengaluru 560038");
            profile.setPhone("+91 98765 43210");
            profile.setFeatured(true);
            profile.setStatus(ProviderStatus.APPROVED);
            return providerRepository.save(profile);
        });
        if (provider.getStatus() != ProviderStatus.APPROVED) {
            provider.setStatus(ProviderStatus.APPROVED);
            providerRepository.save(provider);
        }

        ZoneId zone = ZoneId.of(provider.getTimeZone());
        seedServices(provider);
        seedWeeklyHours(provider);
        LocalDate closedDate = seedClosure(provider, zone);
        seedBookings(provider, customer, zone, closedDate);
        log.info("Demo data seed complete. Accounts: admin={}, provider={}, customer={}. "
                        + "Passwords come from the SEED_* environment variables.",
                adminEmail, providerEmail, customerEmail);
    }

    private User createUserIfMissing(String email, String password, Role role, String fullName, String phone) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return existing.get();
        }
        validatePassword(password, "seed password for " + email);
        User user = User.create(email, passwordEncoder.encode(password), role, fullName, phone);
        userRepository.save(user);
        return user;
    }

    private void seedServices(ProviderProfile provider) {
        List<ServiceDefinition> definitions = List.of(
                new ServiceDefinition("Signature Haircut", 45, "899.00", 0,
                        "A consultation-led cut finished with a wash and style. Includes personalised home-care notes."),
                new ServiceDefinition("Hair Colour", 90, "2499.00", 1,
                        "Full-head ammonia-free colour with a gloss finish. Includes a patch test consultation."),
                new ServiceDefinition("Glow Facial", 60, "1499.00", 2,
                        "A gentle cleanse, exfoliation, massage, and hydrating mask suited to your skin on the day."),
                new ServiceDefinition("Head Massage", 30, "599.00", 3,
                        "A warm-oil scalp and shoulder massage to reset after a long week."),
                new ServiceDefinition("Bridal Makeup", 120, "6999.00", 4,
                        "Trial-informed bridal makeup with long-wear products and a personalised touch-up kit.")
        );
        List<ServiceOffering> existing = serviceRepository
                .findByProviderIdOrderBySortOrderAscNameAsc(provider.getId());
        for (ServiceDefinition definition : definitions) {
            boolean present = existing.stream().anyMatch(service -> service.getName().equals(definition.name()));
            if (present) {
                continue;
            }
            ServiceOffering service = ServiceOffering.create(provider);
            service.setName(definition.name());
            service.setDescription(definition.description());
            service.setDurationMinutes(definition.durationMinutes());
            service.setPrice(new BigDecimal(definition.price()));
            service.setSortOrder(definition.sortOrder());
            service.setActive(true);
            serviceRepository.save(service);
        }
    }

    private void seedWeeklyHours(ProviderProfile provider) {
        if (!weeklyScheduleRepository.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(provider.getId()).isEmpty()) {
            return;
        }
        for (DayOfWeek day : List.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            weeklyScheduleRepository.save(WeeklySchedule.create(provider, day.getValue(),
                    LocalTime.of(10, 0), LocalTime.of(13, 0)));
            weeklyScheduleRepository.save(WeeklySchedule.create(provider, day.getValue(),
                    LocalTime.of(14, 0), LocalTime.of(19, 0)));
        }
    }

    private LocalDate seedClosure(ProviderProfile provider, ZoneId zone) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate candidate = nextWorkingDate(today.plusDays(9), provider, zone);
        boolean exists = exceptionRepository.existsByProviderIdAndDateAndType(provider.getId(), candidate,
                ScheduleExceptionType.CLOSED);
        if (!exists) {
            exceptionRepository.save(ScheduleException.create(provider, candidate,
                    ScheduleExceptionType.CLOSED, null, null, "Studio maintenance day"));
        }
        return candidate;
    }

    private void seedBookings(ProviderProfile provider, User customer, ZoneId zone, LocalDate closedDate) {
        if (!appointmentRepository.findConfirmedFuture(provider.getId(), Instant.now(clock)).isEmpty()) {
            return;
        }
        List<ServiceOffering> services = serviceRepository
                .findByProviderIdOrderBySortOrderAscNameAsc(provider.getId());
        Map<String, ServiceOffering> byName = services.stream()
                .collect(java.util.stream.Collectors.toMap(ServiceOffering::getName, s -> s));
        if (byName.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(zone));

        LocalDate firstDate = nextWorkingDate(today.plusDays(2), provider, zone);
        if (firstDate.equals(closedDate)) {
            firstDate = nextWorkingDate(firstDate.plusDays(1), provider, zone);
        }
        createBooking(provider, customer, byName.get("Glow Facial"), firstDate, LocalTime.of(11, 0), zone,
                null, AppointmentStatus.CONFIRMED);

        LocalDate secondDate = nextWorkingDate(today.plusDays(6), provider, zone);
        if (secondDate.equals(closedDate)) {
            secondDate = nextWorkingDate(secondDate.plusDays(1), provider, zone);
        }
        createBooking(provider, customer, byName.get("Hair Colour"), secondDate, LocalTime.of(16, 0), zone,
                "Please use fragrance-free products.", AppointmentStatus.CONFIRMED);

        LocalDate pastDate = previousWorkingDate(today.minusDays(7), provider);
        createBooking(provider, customer, byName.get("Signature Haircut"), pastDate, LocalTime.of(11, 0), zone,
                null, AppointmentStatus.COMPLETED);

        LocalDate cancelledDate = previousWorkingDate(today.minusDays(12), provider);
        Appointment cancelled = createBooking(provider, customer, byName.get("Head Massage"), cancelledDate,
                LocalTime.of(15, 0), zone, null, AppointmentStatus.CANCELLED);
        if (cancelled != null && cancelled.getCancelledAt() == null) {
            cancelled.cancel(AppointmentActor.CUSTOMER, "Schedule changed", Instant.now(clock));
            appointmentRepository.save(cancelled);
        }
    }

    private Appointment createBooking(ProviderProfile provider, User customer, ServiceOffering service,
                                      LocalDate date, LocalTime time, ZoneId zone, String notes,
                                      AppointmentStatus status) {
        if (service == null) {
            return null;
        }
        Instant start = date.atTime(time).atZone(zone).toInstant();
        Instant end = start.plusSeconds(service.getDurationMinutes() * 60L);
        boolean conflict = !appointmentRepository.findOverlapping(provider.getId(),
                java.util.EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED,
                        AppointmentStatus.NO_SHOW), start, end).isEmpty();
        if (conflict) {
            return null;
        }
        Appointment appointment = Appointment.create(provider, customer, service, start, end, notes);
        if (status == AppointmentStatus.COMPLETED) {
            appointment.markCompleted();
        }
        appointmentRepository.save(appointment);
        return appointment;
    }

    private LocalDate nextWorkingDate(LocalDate start, ProviderProfile provider, ZoneId zone) {
        LocalDate date = start;
        for (int i = 0; i < 14; i++) {
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                return date;
            }
            date = date.plusDays(1);
        }
        return date;
    }

    private LocalDate previousWorkingDate(LocalDate start, ProviderProfile provider) {
        LocalDate date = start;
        for (int i = 0; i < 14; i++) {
            if (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                return date;
            }
            date = date.minusDays(1);
        }
        return date;
    }

    private void validateSeedConfiguration(AppProperties.Seed seed) {
        require(seed.adminEmail(), "SEED_ADMIN_EMAIL");
        require(seed.adminPassword(), "SEED_ADMIN_PASSWORD");
        require(seed.providerEmail(), "SEED_PROVIDER_EMAIL");
        require(seed.providerPassword(), "SEED_PROVIDER_PASSWORD");
        require(seed.customerEmail(), "SEED_CUSTOMER_EMAIL");
        require(seed.customerPassword(), "SEED_CUSTOMER_PASSWORD");
    }

    private void validatePassword(String password, String label) {
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalStateException("The " + label + " does not satisfy the password policy: "
                    + PasswordPolicy.MESSAGE);
        }
    }

    private void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when SEED_ENABLED=true.");
        }
    }

    private record ServiceDefinition(String name, int durationMinutes, String price, int sortOrder,
                                     String description) {
    }
}
