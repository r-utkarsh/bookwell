package com.bookwell.notification;

import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentStatus;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.support.IntegrationTestBase;
import com.bookwell.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLifecycleIT extends IntegrationTestBase {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationProcessor processor;

    @Autowired
    private NotificationRepository notifications;

    @Test
    void bookingCreatesConfirmationAndReminderWithoutDuplicates() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);

        notificationService.bookingCreated(appointment);
        notificationService.bookingCreated(appointment);

        List<Notification> queued = notifications.findByAppointmentIdOrderByCreatedAtAsc(appointment.getId());
        assertThat(queued).hasSize(2);
        assertThat(queued).extracting(Notification::getType)
                .containsExactlyInAnyOrder(NotificationType.BOOKING_CONFIRMATION, NotificationType.REMINDER);
        assertThat(queued).allSatisfy(notification -> {
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getRecipientEmail()).isEqualTo(customer.getEmail());
            assertThat(notification.getDedupeKey()).isNotBlank();
        });

        Notification confirmation = queued.stream()
                .filter(notification -> notification.getType() == NotificationType.BOOKING_CONFIRMATION)
                .findFirst().orElseThrow();
        Notification reminder = queued.stream()
                .filter(notification -> notification.getType() == NotificationType.REMINDER)
                .findFirst().orElseThrow();
        assertThat(confirmation.getScheduledAt()).isEqualTo(fixedNow());
        assertThat(reminder.getScheduledAt())
                .isEqualTo(at(provider, "2027-03-05", "10:00").minus(Duration.ofHours(24)));
    }

    @Test
    void claimComposesMailAndMarksItSentWithDeliveryMode() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);
        notificationService.bookingCreated(appointment);

        Notification confirmation = notifications
                .findByAppointmentIdOrderByCreatedAtAsc(appointment.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.BOOKING_CONFIRMATION)
                .findFirst().orElseThrow();

        NotificationProcessor.ClaimedNotification claimed = processor.claim(confirmation.getId());
        assertThat(claimed).isNotNull();
        assertThat(claimed.message().to()).isEqualTo(customer.getEmail());
        assertThat(claimed.message().subject()).contains(provider.getDisplayName());
        assertThat(claimed.message().text()).contains(service.getName());
        assertThat(claimed.message().html()).contains("View or manage booking");

        Notification sending = notifications.findById(confirmation.getId()).orElseThrow();
        assertThat(sending.getStatus()).isEqualTo(NotificationStatus.SENDING);
        assertThat(sending.getAttempts()).isEqualTo(1);
        assertThat(processor.claim(confirmation.getId())).isNull();

        processor.markSent(confirmation.getId(), "LOG");
        Notification sent = notifications.findById(confirmation.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getDeliveryMode()).isEqualTo("LOG");
        assertThat(sent.getSentAt()).isEqualTo(fixedNow());
        assertThat(sent.getLastError()).isNull();

        processor.markSent(confirmation.getId(), "LOG");
        assertThat(notifications.findById(confirmation.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void futureReminderIsNotClaimableYet() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);
        notificationService.bookingCreated(appointment);

        Notification reminder = notifications.findByAppointmentIdOrderByCreatedAtAsc(appointment.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.REMINDER)
                .findFirst().orElseThrow();

        assertThat(reminder.getScheduledAt()).isAfter(fixedNow());
        assertThat(processor.claim(reminder.getId())).isNull();
        assertThat(notifications.findById(reminder.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void cancellationSkipsPendingReminderAndEnqueuesOneCancellation() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);
        notificationService.bookingCreated(appointment);
        inTx(() -> {
            Appointment managed = appointments.findById(appointment.getId()).orElseThrow();
            managed.cancel(com.bookwell.booking.AppointmentActor.CUSTOMER, "Changed plans", fixedNow());
            appointments.saveAndFlush(managed);
        });

        notificationService.bookingCancelled(appointment);
        notificationService.bookingCancelled(appointment);

        List<Notification> queued = notifications.findByAppointmentIdOrderByCreatedAtAsc(appointment.getId());
        assertThat(queued).hasSize(3);
        assertThat(queued.stream().filter(notification -> notification.getType() == NotificationType.REMINDER)
                .findFirst().orElseThrow().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(queued.stream().filter(notification -> notification.getType() == NotificationType.CANCELLATION)
                .toList()).hasSize(1);
    }

    @Test
    void rescheduleSkipsOldReminderAndQueuesReschedulePlusFreshReminder() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);
        notificationService.bookingCreated(appointment);
        Instant previousStart = appointment.getStartAt();
        Instant newStart = at(provider, "2027-03-06", "11:00");
        Appointment rescheduled = inTx(() -> {
            Appointment managed = appointments.findById(appointment.getId()).orElseThrow();
            managed.reschedule(newStart, newStart.plusSeconds(45 * 60));
            appointments.saveAndFlush(managed);
            return managed;
        });

        notificationService.bookingRescheduled(rescheduled, previousStart);

        List<Notification> queued = notifications.findByAppointmentIdOrderByCreatedAtAsc(appointment.getId());
        assertThat(queued).hasSize(4);
        assertThat(queued.stream().filter(notification -> notification.getType() == NotificationType.REMINDER)
                .filter(notification -> notification.getStatus() == NotificationStatus.SKIPPED)
                .toList()).hasSize(1);
        Notification reschedule = queued.stream()
                .filter(notification -> notification.getType() == NotificationType.RESCHEDULE)
                .findFirst().orElseThrow();
        assertThat(reschedule.getDedupeKey()).endsWith(":1");
        Notification freshReminder = queued.stream()
                .filter(notification -> notification.getType() == NotificationType.REMINDER)
                .filter(notification -> notification.getStatus() == NotificationStatus.PENDING)
                .findFirst().orElseThrow();
        assertThat(freshReminder.getScheduledAt()).isEqualTo(newStart.minus(Duration.ofHours(24)));
    }

    @Test
    void failuresRetryWithBackoffAndStopAtAttemptLimit() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);
        Notification notification = inTx(() -> notifications.saveAndFlush(Notification.create(
                appointment, NotificationType.BOOKING_CONFIRMATION, customer.getEmail(),
                fixedNow(), 2, "TEST:RETRY:" + appointment.getId())));

        assertThat(processor.claim(notification.getId())).isNotNull();
        processor.markFailed(notification.getId(), "SMTP is down");
        Notification retrying = notifications.findById(notification.getId()).orElseThrow();
        assertThat(retrying.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(retrying.getAttempts()).isEqualTo(1);
        assertThat(retrying.getLastError()).isEqualTo("SMTP is down");
        assertThat(retrying.getScheduledAt())
                .isEqualTo(fixedNow().plus(NotificationService.backoff(1)));

        // Make the retry due now, then exhaust the configured attempt budget.
        inTx(() -> {
            Notification due = notifications.findById(notification.getId()).orElseThrow();
            due.setScheduledAt(fixedNow());
            notifications.saveAndFlush(due);
        });
        assertThat(processor.claim(notification.getId())).isNotNull();
        processor.markFailed(notification.getId(), "Still down");

        Notification failed = notifications.findById(notification.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(2);
        assertThat(failed.getLastError()).isEqualTo("Still down");
        assertThat(processor.claim(notification.getId())).isNull();
    }

    @Test
    void staleSendingClaimsAreRecoveredOrFailedAtTheLimit() {
        ProviderProfile provider = newProvider();
        ServiceOffering service = newService(provider, 45);
        User customer = newUser(com.bookwell.user.Role.CUSTOMER);
        Appointment appointment = newAppointment(provider, customer, service,
                at(provider, "2027-03-05", "10:00"), AppointmentStatus.CONFIRMED);

        Notification retryable = inTx(() -> notifications.saveAndFlush(Notification.create(
                appointment, NotificationType.BOOKING_CONFIRMATION, customer.getEmail(),
                fixedNow(), 5, "TEST:STALE:A:" + appointment.getId())));
        Notification exhausted = inTx(() -> notifications.saveAndFlush(Notification.create(
                appointment, NotificationType.REMINDER, customer.getEmail(),
                fixedNow(), 1, "TEST:STALE:B:" + appointment.getId())));

        assertThat(processor.claim(retryable.getId())).isNotNull();
        assertThat(processor.claim(exhausted.getId())).isNotNull();

        jdbcTemplate.update("update notifications set updated_at = now() - interval '20 minutes' where id in (?, ?)",
                retryable.getId(), exhausted.getId());
        int recovered = processor.recoverStale();

        assertThat(recovered).isEqualTo(2);
        Notification requeued = notifications.findById(retryable.getId()).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(requeued.getScheduledAt()).isEqualTo(fixedNow());
        Notification failed = notifications.findById(exhausted.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }
}
