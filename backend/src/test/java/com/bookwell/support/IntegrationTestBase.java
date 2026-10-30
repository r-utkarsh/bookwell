package com.bookwell.support;

import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentActor;
import com.bookwell.booking.AppointmentRepository;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.security.AuthUser;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared PostgreSQL Testcontainer and fixtures for integration tests.
 * The container is a JVM-wide singleton so all test classes reuse one database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final String TEST_PASSWORD = "TestPass!2026";
    public static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("bookwell_test")
                    .withUsername("bookwell")
                    .withPassword("bookwell");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected UserRepository users;

    @Autowired
    protected ProviderProfileRepository providers;

    @Autowired
    protected ServiceOfferingRepository services;

    @Autowired
    protected WeeklyScheduleRepository weeklySchedules;

    @Autowired
    protected ScheduleExceptionRepository scheduleExceptions;

    @Autowired
    protected AppointmentRepository appointments;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected <T> T inTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    protected void inTx(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12) + "@test.bookwell.local";
    }

    protected User newUser(Role role) {
        return newUser(role, TEST_PASSWORD);
    }

    protected User newUser(Role role, String rawPassword) {
        String email = uniqueEmail(role.name().toLowerCase());
        return inTx(() -> users.save(User.create(
                email,
                passwordEncoder.encode(rawPassword),
                role,
                "Test " + role.name().toLowerCase(),
                "+91 90000 00000")));
    }

    protected ProviderProfile newProvider() {
        return newProvider(ProviderStatus.APPROVED);
    }

    protected ProviderProfile newProvider(ProviderStatus status) {
        User owner = newUser(Role.PROVIDER);
        String token = UUID.randomUUID().toString().substring(0, 8);
        ProviderProfile profile = ProviderProfile.create(
                owner,
                "Provider " + token,
                "Studio " + token,
                "Salon & Spa",
                "Bengaluru",
                "Asia/Kolkata",
                "INR");
        profile.setDescription("Integration test studio.");
        profile.setAddress("1 Test Road, Bengaluru");
        profile.setStatus(status);
        return inTx(() -> providers.save(profile));
    }

    protected User providerOwner(ProviderProfile provider) {
        return inTx(() -> providers.findDetailedById(provider.getId())
                .orElseThrow()
                .getUser());
    }

    protected ServiceOffering newService(ProviderProfile provider, int durationMinutes) {
        return newService(provider, durationMinutes, new BigDecimal("999.00"));
    }

    protected ServiceOffering newService(ProviderProfile provider, int durationMinutes, BigDecimal price) {
        String token = UUID.randomUUID().toString().substring(0, 6);
        ServiceOffering service = ServiceOffering.create(provider);
        service.setName("Test service " + token);
        service.setDescription("A test service.");
        service.setDurationMinutes(durationMinutes);
        service.setPrice(price);
        service.setActive(true);
        return inTx(() -> services.save(service));
    }

    protected WeeklySchedule addWeeklyHours(ProviderProfile provider, int dayOfWeek,
                                            String start, String end) {
        return inTx(() -> weeklySchedules.save(WeeklySchedule.create(
                provider, dayOfWeek, LocalTime.parse(start), LocalTime.parse(end))));
    }

    protected void addWeeklyHoursEveryDay(ProviderProfile provider, String start, String end) {
        for (int day = 1; day <= 7; day++) {
            addWeeklyHours(provider, day, start, end);
        }
    }

    protected ScheduleException closeDate(ProviderProfile provider, LocalDate date) {
        return inTx(() -> scheduleExceptions.save(ScheduleException.create(
                provider, date, ScheduleExceptionType.CLOSED, null, null, "Test closure")));
    }

    protected ScheduleException addException(ProviderProfile provider, LocalDate date, ScheduleExceptionType type,
                                             String start, String end) {
        return inTx(() -> scheduleExceptions.save(ScheduleException.create(
                provider, date, type,
                start == null ? null : LocalTime.parse(start),
                end == null ? null : LocalTime.parse(end),
                "Test exception")));
    }

    protected Appointment newAppointment(ProviderProfile provider, User customer, ServiceOffering service,
                                         Instant start, AppointmentStatus status) {
        Instant end = start.plusSeconds(service.getDurationMinutes() * 60L);
        Appointment appointment = Appointment.create(provider, customer, service, start, end, "Test booking");
        if (status == AppointmentStatus.CANCELLED) {
            appointment.cancel(AppointmentActor.CUSTOMER, "Test cancellation", Instant.now());
        } else if (status == AppointmentStatus.COMPLETED) {
            appointment.markCompleted();
        } else if (status == AppointmentStatus.NO_SHOW) {
            appointment.markNoShow();
        }
        return inTx(() -> appointments.saveAndFlush(appointment));
    }

    protected Instant at(ProviderProfile provider, String date, String time) {
        return LocalDate.parse(date)
                .atTime(LocalTime.parse(time))
                .atZone(ZoneId.of(provider.getTimeZone()))
                .toInstant();
    }

    protected AuthUser auth(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isEnabled());
    }

    protected static Instant fixedNow() {
        return TestClockConfig.FIXED_INSTANT;
    }
}
