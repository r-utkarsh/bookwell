package com.bookwell.availability;

import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.exception.ApiException;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ProviderStatus;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.schedule.ScheduleExceptionType;
import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityIT extends IntegrationTestBase {

    private static final LocalDate FRIDAY = LocalDate.parse("2027-03-05");

    @Autowired
    private AvailabilityService availabilityService;

    @Test
    void buildsSlotsAroundBreakAndExistingAppointmentUsingHalfOpenIntervals() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);

        addException(provider, FRIDAY, ScheduleExceptionType.BLOCKED, "10:00", "10:30");
        newAppointment(provider, customer, service, at(provider, "2027-03-05", "11:30"),
                AppointmentStatus.CONFIRMED);

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, FRIDAY, fixedNow());

        assertThat(starts(result)).containsExactly(
                at(provider, "2027-03-05", "09:00"),
                at(provider, "2027-03-05", "09:15"),
                at(provider, "2027-03-05", "10:30"),
                at(provider, "2027-03-05", "10:45"));
        assertThat(result.message()).isNull();
        // A 10:45 slot ending exactly at the 11:30 appointment is allowed.
        assertThat(result.slots().getLast().end()).isEqualTo(at(provider, "2027-03-05", "11:30"));
    }

    @Test
    void usesServiceDurationAndConfiguredStartGrid() {
        ProviderProfile provider = newProvider();
        addWeeklyHours(provider, FRIDAY.getDayOfWeek().getValue(), "10:00", "12:00");
        ServiceOffering service = newService(provider, 45);

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, FRIDAY, fixedNow());

        assertThat(result.slots()).hasSize(6);
        assertThat(result.slots().getFirst().start()).isEqualTo(at(provider, "2027-03-05", "10:00"));
        assertThat(result.slots().getLast().start()).isEqualTo(at(provider, "2027-03-05", "11:15"));
        assertThat(result.slots().getLast().end()).isEqualTo(at(provider, "2027-03-05", "12:00"));
        assertThat(result.slots()).allSatisfy(slot ->
                assertThat(slot.end()).isEqualTo(slot.start().plusSeconds(45 * 60)));
        assertThat(result.slots().getFirst().label()).isEqualTo("10:00 AM");
    }

    @Test
    void fullDayClosureRemovesAvailability() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "09:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        closeDate(provider, FRIDAY);

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, FRIDAY, fixedNow());

        assertThat(result.slots()).isEmpty();
        assertThat(result.message()).isEqualTo("The provider is closed on this date.");
    }

    @Test
    void extraAvailabilityAddsSlotsOnAnOtherwiseClosedDay() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        LocalDate sunday = LocalDate.parse("2027-03-07");
        addException(provider, sunday, ScheduleExceptionType.EXTRA_AVAILABILITY, "10:00", "11:00");

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, sunday, fixedNow());

        assertThat(starts(result)).containsExactly(
                at(provider, "2027-03-07", "10:00"),
                at(provider, "2027-03-07", "10:15"));
    }

    @Test
    void cancelledAppointmentsDoNotBlockSlots() {
        ProviderProfile provider = newProvider();
        addWeeklyHours(provider, FRIDAY.getDayOfWeek().getValue(), "10:00", "12:00");
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        newAppointment(provider, customer, service, at(provider, "2027-03-05", "10:00"),
                AppointmentStatus.CANCELLED);

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, FRIDAY, fixedNow());

        assertThat(result.slots()).hasSize(6);
        assertThat(result.slots().getFirst().start()).isEqualTo(at(provider, "2027-03-05", "10:00"));
    }

    @Test
    void appliesLeadTimePastDateAndHorizonRules() {
        ProviderProfile provider = newProvider();
        addWeeklyHoursEveryDay(provider, "05:00", "08:00");
        ServiceOffering service = newService(provider, 45);

        AvailabilityService.Calculation today = availabilityService.calculate(
                provider, service, LocalDate.parse("2027-03-01"), fixedNow());
        // Fixed "now" is 05:30 IST; the 30 minute lead time makes 06:00 the first valid start.
        assertThat(today.slots()).isNotEmpty();
        assertThat(today.slots().getFirst().start()).isEqualTo(at(provider, "2027-03-01", "06:00"));
        assertThat(today.slots().getFirst().start()).isEqualTo(fixedNow().plusSeconds(30 * 60));

        AvailabilityService.Calculation past = availabilityService.calculate(
                provider, service, LocalDate.parse("2027-02-28"), fixedNow());
        assertThat(past.slots()).isEmpty();
        assertThat(past.message()).isEqualTo("Choose today or a future date.");

        AvailabilityService.Calculation tooFar = availabilityService.calculate(
                provider, service, LocalDate.parse("2027-05-02"), fixedNow());
        assertThat(tooFar.slots()).isEmpty();
        assertThat(tooFar.message()).contains("60 days ahead");
    }

    @Test
    void rejectsUnapprovedProviderOrInactiveService() {
        ProviderProfile pending = newProvider(ProviderStatus.PENDING);
        addWeeklyHoursEveryDay(pending, "09:00", "12:00");
        ServiceOffering pendingService = newService(pending, 45);

        assertThatThrownBy(() -> availabilityService.get(
                pending.getId(), pendingService.getId(), FRIDAY))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(404);

        ProviderProfile approved = newProvider();
        addWeeklyHoursEveryDay(approved, "09:00", "12:00");
        ServiceOffering inactive = newService(approved, 45);
        inTx(() -> {
            inactive.setActive(false);
            services.save(inactive);
        });

        assertThatThrownBy(() -> availabilityService.get(
                approved.getId(), inactive.getId(), FRIDAY))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(404);
    }

    @Test
    void providerTimezoneControlsTheLocalDay() {
        ProviderProfile provider = newProvider();
        inTx(() -> {
            provider.setTimeZone("America/New_York");
            providers.save(provider);
        });
        addWeeklyHoursEveryDay(provider, "09:00", "10:00");
        ServiceOffering service = newService(provider, 45);

        AvailabilityService.Calculation result = availabilityService.calculate(
                provider, service, LocalDate.parse("2027-03-05"), fixedNow());

        assertThat(result.slots()).hasSize(2);
        assertThat(result.slots().getFirst().start())
                .isEqualTo(LocalDate.parse("2027-03-05").atTime(LocalTime.parse("09:00"))
                        .atZone(ZoneId.of("America/New_York")).toInstant());
    }

    private List<java.time.Instant> starts(AvailabilityService.Calculation calculation) {
        return calculation.slots().stream().map(AvailabilityService.Slot::start).toList();
    }
}
