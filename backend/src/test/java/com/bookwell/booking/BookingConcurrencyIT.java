package com.bookwell.booking;

import com.bookwell.common.exception.BookingConflictException;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL concurrency proof. The database exclusion constraint, not a
 * JVM lock, is what makes these races safe.
 */
class BookingConcurrencyIT extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;

    @Test
    void twoCustomersRacingForTheSameSlotProduceExactlyOneBooking() throws Exception {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User first = newUser(com.bookwell.user.Role.CUSTOMER);
        User second = newUser(com.bookwell.user.Role.CUSTOMER);
        Instant start = at(provider, "2027-03-05", "10:00");

        List<Outcome> outcomes = race(
                () -> bookingService.create(auth(first), request(provider, service, start)),
                () -> bookingService.create(auth(second), request(provider, service, start)));

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null).hasSize(1)
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(BookingConflictException.class));
        assertThat(appointments.findOverlapping(provider.getId(),
                Set.of(AppointmentStatus.CONFIRMED), start.minusSeconds(1), start.plusSeconds(3600)))
                .hasSize(1);
    }

    @Test
    void overlappingButNonIdenticalIntervalsCannotBothBeBooked() throws Exception {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User first = newUser(com.bookwell.user.Role.CUSTOMER);
        User second = newUser(com.bookwell.user.Role.CUSTOMER);
        Instant firstStart = at(provider, "2027-03-05", "10:00");
        Instant secondStart = at(provider, "2027-03-05", "10:15");

        List<Outcome> outcomes = race(
                () -> bookingService.create(auth(first), request(provider, service, firstStart)),
                () -> bookingService.create(auth(second), request(provider, service, secondStart)));

        assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null).hasSize(1)
                .allSatisfy(outcome -> assertThat(outcome.error())
                        .isInstanceOf(BookingConflictException.class));
        assertThat(appointments.findOverlapping(provider.getId(),
                Set.of(AppointmentStatus.CONFIRMED), firstStart.minusSeconds(1), secondStart.plusSeconds(3600)))
                .hasSize(1);
    }

    @Test
    void databaseExclusionConstraintRejectsConcurrentOverlappingInserts() throws Exception {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User first = newUser(com.bookwell.user.Role.CUSTOMER);
        User second = newUser(com.bookwell.user.Role.CUSTOMER);

        Instant aStart = at(provider, "2027-03-05", "10:00");
        Instant bStart = at(provider, "2027-03-05", "10:15");

        List<InsertOutcome> outcomes = raceInserts(
                () -> insertAppointment(provider, first, service, aStart),
                () -> insertAppointment(provider, second, service, bStart));

        assertThat(outcomes).filteredOn(InsertOutcome::success).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.error() != null).hasSize(1)
                .allSatisfy(outcome -> {
                    Throwable error = outcome.error();
                    assertThat(error).isInstanceOf(DataAccessException.class);
                    assertThat(sqlState(error)).isEqualTo("23P01");
                });
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from appointments where provider_id = ?",
                Long.class, provider.getId())).isEqualTo(1L);
    }

    @Test
    void touchingIntervalsAreAllowedBecauseRangesAreHalfOpen() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User first = newUser(com.bookwell.user.Role.CUSTOMER);
        User second = newUser(com.bookwell.user.Role.CUSTOMER);

        Instant firstStart = at(provider, "2027-03-05", "10:00");
        Instant secondStart = at(provider, "2027-03-05", "10:45");

        BookingDtos.AppointmentDto firstBooking = bookingService.create(
                auth(first), request(provider, service, firstStart));
        BookingDtos.AppointmentDto secondBooking = bookingService.create(
                auth(second), request(provider, service, secondStart));

        assertThat(firstBooking.endAt()).isEqualTo(secondBooking.startAt());
        assertThat(firstBooking.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(secondBooking.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointments.findOverlapping(provider.getId(),
                Set.of(AppointmentStatus.CONFIRMED), firstStart.minusSeconds(1), secondStart.plusSeconds(3600)))
                .hasSize(2);
    }

    @Test
    void cancelledAppointmentReleasesTheSlotForAnotherCustomer() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User first = newUser(com.bookwell.user.Role.CUSTOMER);
        User second = newUser(com.bookwell.user.Role.CUSTOMER);
        Instant start = at(provider, "2027-03-05", "10:00");

        BookingDtos.AppointmentDto original = bookingService.create(
                auth(first), request(provider, service, start));
        bookingService.cancel(auth(first), original.id(), "Plans changed");

        BookingDtos.AppointmentDto replacement = bookingService.create(
                auth(second), request(provider, service, start));

        assertThat(replacement.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(replacement.customerId()).isEqualTo(second.getId());
        assertThat(appointments.findById(original.id()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void databaseConstraintAlsoRejectsNonNullIdenticalCollision() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Instant start = at(provider, "2027-03-05", "10:00");

        insertAppointment(provider, customer, service, start);

        assertThatThrownBy(() -> insertAppointment(provider, customer, service, start))
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from appointments where provider_id = ?",
                Long.class, provider.getId())).isEqualTo(1L);
    }

    private BookingDtos.CreateAppointmentRequest request(ProviderProfile provider, ServiceOffering service,
                                                         Instant start) {
        return new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(), start, "Race booking");
    }

    private void insertAppointment(ProviderProfile provider, User customer, ServiceOffering service, Instant start) {
        jdbcTemplate.update("""
                        insert into appointments (
                            id, provider_id, customer_id, service_id, start_ts, end_ts, status,
                            service_name, service_duration_minutes, service_price, currency,
                            customer_name, customer_email, customer_notes, reschedule_count
                        ) values (?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                UUID.randomUUID(), provider.getId(), customer.getId(), service.getId(),
                Timestamp.from(start),
                Timestamp.from(start.plusSeconds(service.getDurationMinutes() * 60L)),
                service.getName(), service.getDurationMinutes(), service.getPrice(), service.getCurrency(),
                customer.getFullName(), customer.getEmail(), "Concurrent insert");
    }

    private List<Outcome> race(Callable<BookingDtos.AppointmentDto> first,
                               Callable<BookingDtos.AppointmentDto> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<Outcome>> tasks = List.of(
                    () -> runBooking(first, ready, start),
                    () -> runBooking(second, ready, start));
            List<Future<Outcome>> futures = tasks.stream().map(executor::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(this::await).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<InsertOutcome> raceInserts(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<InsertOutcome>> tasks = List.of(
                    () -> runInsert(first, ready, start),
                    () -> runInsert(second, ready, start));
            List<Future<InsertOutcome>> futures = tasks.stream().map(executor::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(this::await).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private Outcome runBooking(Callable<BookingDtos.AppointmentDto> action,
                               CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            return new Outcome(action.call(), null);
        } catch (Throwable error) {
            return new Outcome(null, error);
        }
    }

    private InsertOutcome runInsert(Runnable action, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await(10, TimeUnit.SECONDS);
        try {
            action.run();
            return new InsertOutcome(true, null);
        } catch (Throwable error) {
            return new InsertOutcome(false, error);
        }
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("Concurrent task failed to complete", ex);
        }
    }

    private String sqlState(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            current = current.getCause();
        }
        return null;
    }

    private record Outcome(BookingDtos.AppointmentDto appointment, Throwable error) {
        boolean success() {
            return appointment != null && error == null;
        }
    }

    private record InsertOutcome(boolean success, Throwable error) {
    }
}
