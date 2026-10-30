package com.bookwell.booking;

import com.bookwell.common.exception.ApiException;
import com.bookwell.common.exception.BookingConflictException;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingRulesIT extends IntegrationTestBase {

    @Autowired
    private BookingService bookingService;

    @Test
    void customerCannotCancelInsideTheTwoHourCutoffButProviderAndAdminCan() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        User admin = newUser(com.bookwell.user.Role.ADMIN);

        Appointment soon = newAppointment(provider, customer, service,
                fixedNow().plus(Duration.ofHours(1)), AppointmentStatus.CONFIRMED);

        BookingDtos.AppointmentDto view = bookingService.get(auth(customer), soon.getId());
        assertThat(view.canCancel()).isFalse();
        assertThat(view.canReschedule()).isFalse();

        assertThatThrownBy(() -> bookingService.cancel(auth(customer), soon.getId(), "Too late"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus()).isEqualTo(409);
                    assertThat(api.getCode()).isEqualTo("CANCELLATION_CUTOFF");
                });

        BookingDtos.AppointmentDto providerCancellation = bookingService.cancel(
                auth(providerOwner(provider)), soon.getId(), "Provider emergency");
        assertThat(providerCancellation.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(providerCancellation.cancelledBy()).isEqualTo(AppointmentActor.PROVIDER);

        Appointment soonForAdmin = newAppointment(provider, customer, service,
                fixedNow().plus(Duration.ofHours(1)), AppointmentStatus.CONFIRMED);
        BookingDtos.AppointmentDto adminCancellation = bookingService.cancel(
                auth(admin), soonForAdmin.getId(), "Administrative cancellation");
        assertThat(adminCancellation.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(adminCancellation.cancelledBy()).isEqualTo(AppointmentActor.ADMIN);
    }

    @Test
    void customerCanCancelOutsideTheCutoffAndCannotCancelTwice() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                fixedNow().plus(Duration.ofHours(3)), AppointmentStatus.CONFIRMED);

        assertThat(bookingService.get(auth(customer), appointment.getId()).canCancel()).isTrue();

        BookingDtos.AppointmentDto cancelled = bookingService.cancel(
                auth(customer), appointment.getId(), "Plans changed");
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("Plans changed");
        assertThat(cancelled.cancelledBy()).isEqualTo(AppointmentActor.CUSTOMER);

        assertThatThrownBy(() -> bookingService.cancel(auth(customer), appointment.getId(), "Again"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENT_NOT_MODIFIABLE"));
    }

    @Test
    void rescheduleUpdatesTimeAndRecordsPreviousStart() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Instant originalStart = at(provider, "2027-03-05", "10:00");
        Instant newStart = at(provider, "2027-03-05", "11:00");

        BookingDtos.AppointmentDto booking = bookingService.create(
                auth(customer), new BookingDtos.CreateAppointmentRequest(
                        provider.getId(), service.getId(), originalStart, null));

        BookingDtos.AppointmentDto moved = bookingService.reschedule(
                auth(customer), booking.id(), new BookingDtos.RescheduleRequest(newStart));

        assertThat(moved.startAt()).isEqualTo(newStart);
        assertThat(moved.endAt()).isEqualTo(newStart.plusSeconds(45 * 60));
        assertThat(moved.rescheduleCount()).isEqualTo(1);
        assertThat(moved.rescheduledFromAt()).isEqualTo(originalStart);
        assertThat(moved.canReschedule()).isTrue();
    }

    @Test
    void rescheduleCannotCollideWithAnotherBooking() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45);
        User firstCustomer = newUser(com.bookwell.user.Role.CUSTOMER);
        User secondCustomer = newUser(com.bookwell.user.Role.CUSTOMER);

        BookingDtos.AppointmentDto first = bookingService.create(auth(firstCustomer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "10:00"), null));
        BookingDtos.AppointmentDto second = bookingService.create(auth(secondCustomer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "11:00"), null));

        assertThatThrownBy(() -> bookingService.reschedule(auth(secondCustomer), second.id(),
                new BookingDtos.RescheduleRequest(at(provider, "2027-03-05", "10:15"))))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(409));

        assertThat(bookingService.get(auth(secondCustomer), second.id()).startAt())
                .isEqualTo(at(provider, "2027-03-05", "11:00"));
        assertThat(bookingService.get(auth(firstCustomer), first.id()).startAt())
                .isEqualTo(at(provider, "2027-03-05", "10:00"));
    }

    @Test
    void customerCutoffAlsoAppliesToReschedulingWhileProviderCanMoveIt() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "00:00", "23:59");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment soon = newAppointment(provider, customer, service,
                fixedNow().plus(Duration.ofHours(1)), AppointmentStatus.CONFIRMED);
        Instant target = fixedNow().plus(Duration.ofHours(3));

        assertThatThrownBy(() -> bookingService.reschedule(auth(customer), soon.getId(),
                new BookingDtos.RescheduleRequest(target)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("CANCELLATION_CUTOFF"));

        BookingDtos.AppointmentDto moved = bookingService.reschedule(
                auth(providerOwner(provider)), soon.getId(), new BookingDtos.RescheduleRequest(target));
        assertThat(moved.startAt()).isEqualTo(target);
        assertThat(moved.rescheduleCount()).isEqualTo(1);
    }

    @Test
    void serviceSnapshotSurvivesLaterCatalogueChanges() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45, new BigDecimal("1200.00"));
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        String originalName = service.getName();

        BookingDtos.AppointmentDto booking = bookingService.create(auth(customer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "10:00"), "Keep my details"));

        inTx(() -> {
            ServiceOffering managed = services.findById(service.getId()).orElseThrow();
            managed.setName("Renamed later");
            managed.setPrice(new BigDecimal("1800.00"));
            managed.setDurationMinutes(30);
            services.saveAndFlush(managed);
        });

        BookingDtos.AppointmentDto reloaded = bookingService.get(auth(customer), booking.id());
        assertThat(reloaded.serviceName()).isEqualTo(originalName);
        assertThat(reloaded.serviceDurationMinutes()).isEqualTo(45);
        assertThat(reloaded.servicePrice()).isEqualByComparingTo("1200.00");
        assertThat(reloaded.currency()).isEqualTo("INR");
        assertThat(reloaded.customerNotes()).isEqualTo("Keep my details");
        assertThat(reloaded.canReschedule()).isFalse();
    }

    @Test
    void ownershipIsEnforcedForCustomersProvidersAndAdmins() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        User otherCustomer = newUser(com.bookwell.user.Role.CUSTOMER);
        ProviderProfile otherProvider = newProvider();
        User admin = newUser(com.bookwell.user.Role.ADMIN);

        BookingDtos.AppointmentDto booking = bookingService.create(auth(customer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "10:00"), null));

        assertThatThrownBy(() -> bookingService.get(auth(otherCustomer), booking.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));

        assertThatThrownBy(() -> bookingService.get(auth(providerOwner(otherProvider)), booking.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));

        assertThat(bookingService.get(auth(providerOwner(provider)), booking.id()).id())
                .isEqualTo(booking.id());
        assertThat(bookingService.get(auth(admin), booking.id()).id()).isEqualTo(booking.id());

        assertThatThrownBy(() -> bookingService.cancel(auth(otherCustomer), booking.id(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(403));
    }

    @Test
    void statusTransitionsRequireTheAppointmentToHaveReachedTheRightPoint() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);

        Appointment upcoming = newAppointment(provider, customer, service,
                fixedNow().plus(Duration.ofHours(1)), AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> bookingService.markNoShow(auth(providerOwner(provider)), upcoming.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENT_NOT_STARTED"));

        Appointment running = newAppointment(provider, customer, service,
                fixedNow().minus(Duration.ofMinutes(10)), AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> bookingService.markCompleted(auth(providerOwner(provider)), running.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENT_NOT_FINISHED"));
        assertThat(bookingService.markNoShow(auth(providerOwner(provider)), running.getId()).status())
                .isEqualTo(AppointmentStatus.NO_SHOW);

        Appointment finished = newAppointment(provider, customer, service,
                fixedNow().minus(Duration.ofHours(2)), AppointmentStatus.CONFIRMED);
        assertThat(bookingService.markCompleted(auth(providerOwner(provider)), finished.getId()).status())
                .isEqualTo(AppointmentStatus.COMPLETED);

        assertThatThrownBy(() -> bookingService.markCompleted(auth(providerOwner(provider)), finished.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENT_NOT_COMPLETABLE"));
    }

    @Test
    void customerUpcomingAndHistoryScopesSeparateFutureAndFinishedBookings() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);

        BookingDtos.AppointmentDto upcoming = bookingService.create(auth(customer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "10:00"), null));
        Appointment past = newAppointment(provider, customer, service,
                fixedNow().minus(Duration.ofDays(2)), AppointmentStatus.COMPLETED);

        var upcomingPage = bookingService.listForCustomer(auth(customer), "upcoming", 0, 10);
        var historyPage = bookingService.listForCustomer(auth(customer), "history", 0, 10);

        assertThat(upcomingPage.content()).extracting(BookingDtos.AppointmentDto::id)
                .contains(upcoming.id())
                .doesNotContain(past.getId());
        assertThat(historyPage.content()).extracting(BookingDtos.AppointmentDto::id)
                .contains(past.getId())
                .doesNotContain(upcoming.id());

        assertThatThrownBy(() -> bookingService.listForCustomer(auth(customer), "everything", 0, 10))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("INVALID_SCOPE"));
    }

    @Test
    void rejectsNonMinuteAlignedOrPastStartTimes() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "13:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);

        assertThatThrownBy(() -> bookingService.create(auth(customer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        at(provider, "2027-03-05", "10:00").plusSeconds(30), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("INVALID_START_TIME"));

        assertThatThrownBy(() -> bookingService.create(auth(customer),
                new BookingDtos.CreateAppointmentRequest(provider.getId(), service.getId(),
                        fixedNow().minusSeconds(60), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("PAST_APPOINTMENT"));
    }
}
