package com.bookwell.schedule;

import com.bookwell.booking.AppointmentStatus;
import com.bookwell.common.exception.ApiException;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleRulesIT extends IntegrationTestBase {

    private static final LocalDate FRIDAY = LocalDate.parse("2027-03-05");

    @Autowired
    private ScheduleService scheduleService;

    @Test
    void rejectsOverlappingWeeklyIntervalsBeforeWriting() {
        ProviderProfile provider = newProvider();
        User owner = providerOwner(provider);

        ScheduleDtos.ReplaceWeeklyScheduleRequest request = new ScheduleDtos.ReplaceWeeklyScheduleRequest(List.of(
                new ScheduleDtos.WeeklyIntervalRequest(5, LocalTime.parse("09:00"), LocalTime.parse("12:00")),
                new ScheduleDtos.WeeklyIntervalRequest(5, LocalTime.parse("11:00"), LocalTime.parse("13:00"))));

        assertThatThrownBy(() -> scheduleService.replaceWeekly(owner.getId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("OVERLAPPING_INTERVALS"));
        assertThat(weeklySchedules.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(provider.getId())).isEmpty();
    }

    @Test
    void databaseAlsoRejectsOverlappingWeeklyRows() {
        ProviderProfile provider = newProvider();
        addWeeklyHours(provider, 5, "09:00", "12:00");

        assertThatThrownBy(() -> inTx(() -> weeklySchedules.saveAndFlush(
                WeeklySchedule.create(provider, 5, LocalTime.parse("11:00"), LocalTime.parse("13:00")))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void replacingHoursCannotStrandAnUpcomingAppointment() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        User owner = providerOwner(provider);
        newAppointment(provider, customer, service, at(provider, "2027-03-05", "10:00"),
                AppointmentStatus.CONFIRMED);

        ScheduleDtos.ReplaceWeeklyScheduleRequest narrower = new ScheduleDtos.ReplaceWeeklyScheduleRequest(List.of(
                new ScheduleDtos.WeeklyIntervalRequest(5, LocalTime.parse("11:00"), LocalTime.parse("13:00"))));

        assertThatThrownBy(() -> scheduleService.replaceWeekly(owner.getId(), narrower))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENT_OUTSIDE_SCHEDULE"));
        assertThat(weeklySchedules.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(provider.getId())).isEmpty();
    }

    @Test
    void cannotBlockOrCloseOverAConfirmedAppointment() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        User owner = providerOwner(provider);
        newAppointment(provider, customer, service, at(provider, "2027-03-05", "10:00"),
                AppointmentStatus.CONFIRMED);

        ScheduleDtos.CreateExceptionRequest block = new ScheduleDtos.CreateExceptionRequest(
                FRIDAY, ScheduleExceptionType.BLOCKED, LocalTime.parse("10:00"), LocalTime.parse("11:00"), "Break");
        assertThatThrownBy(() -> scheduleService.createException(owner.getId(), block))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENTS_EXIST"));

        ScheduleDtos.CreateExceptionRequest closure = new ScheduleDtos.CreateExceptionRequest(
                FRIDAY, ScheduleExceptionType.CLOSED, null, null, "Closed");
        assertThatThrownBy(() -> scheduleService.createException(owner.getId(), closure))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("APPOINTMENTS_EXIST"));
    }

    @Test
    void createsValidExceptionsAndPreventsDuplicateClosures() {
        ProviderProfile provider = newProvider();
        User owner = providerOwner(provider);

        ScheduleDtos.ExceptionDto closure = scheduleService.createException(owner.getId(),
                new ScheduleDtos.CreateExceptionRequest(FRIDAY, ScheduleExceptionType.CLOSED,
                        null, null, "Studio maintenance"));
        assertThat(closure.type()).isEqualTo(ScheduleExceptionType.CLOSED);

        assertThatThrownBy(() -> scheduleService.createException(owner.getId(),
                new ScheduleDtos.CreateExceptionRequest(FRIDAY, ScheduleExceptionType.CLOSED,
                        null, null, "Another closure")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("EXCEPTION_EXISTS"));

        ScheduleDtos.ExceptionDto blocked = scheduleService.createException(owner.getId(),
                new ScheduleDtos.CreateExceptionRequest(FRIDAY.plusDays(1), ScheduleExceptionType.BLOCKED,
                        LocalTime.parse("12:00"), LocalTime.parse("13:00"), "Lunch"));
        assertThat(blocked.type()).isEqualTo(ScheduleExceptionType.BLOCKED);

        ScheduleDtos.ScheduleOverview overview = scheduleService.overview(owner.getId(), FRIDAY);
        assertThat(overview.upcomingExceptions()).extracting(ScheduleDtos.ExceptionDto::id)
                .contains(closure.id(), blocked.id());
        assertThat(overview.timeZone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void validatesExceptionShapeDateAndOwnership() {
        ProviderProfile provider = newProvider();
        User owner = providerOwner(provider);

        assertThatThrownBy(() -> scheduleService.createException(owner.getId(),
                new ScheduleDtos.CreateExceptionRequest(FRIDAY, ScheduleExceptionType.CLOSED,
                        LocalTime.parse("10:00"), LocalTime.parse("11:00"), "Bad shape")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("INVALID_EXCEPTION"));

        assertThatThrownBy(() -> scheduleService.createException(owner.getId(),
                new ScheduleDtos.CreateExceptionRequest(LocalDate.parse("2027-02-27"),
                        ScheduleExceptionType.CLOSED, null, null, "Past")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("PAST_DATE"));

        ScheduleDtos.ExceptionDto otherProviderException = scheduleService.createException(
                providerOwner(newProvider()).getId(),
                new ScheduleDtos.CreateExceptionRequest(FRIDAY, ScheduleExceptionType.CLOSED,
                        null, null, "Other studio"));
        assertThatThrownBy(() -> scheduleService.deleteException(owner.getId(), otherProviderException.id()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus()).isEqualTo(404));
    }
}
