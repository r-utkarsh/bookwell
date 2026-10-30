package com.bookwell.notification;

import com.bookwell.booking.Appointment;
import com.bookwell.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AppProperties properties;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                               AppProperties properties,
                               Clock clock) {
        this.notificationRepository = notificationRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void bookingCreated(Appointment appointment) {
        if (!properties.notifications().enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        enqueue(appointment, NotificationType.BOOKING_CONFIRMATION, now,
                "CONFIRM:" + appointment.getId());
        enqueueReminder(appointment, now);
    }

    @Transactional
    public void bookingCancelled(Appointment appointment) {
        if (!properties.notifications().enabled()) {
            return;
        }
        skipPendingReminders(appointment.getId(), "Appointment was cancelled before this reminder was sent.");
        enqueue(appointment, NotificationType.CANCELLATION, Instant.now(clock),
                "CANCEL:" + appointment.getId());
    }

    @Transactional
    public void bookingRescheduled(Appointment appointment, Instant previousStart) {
        if (!properties.notifications().enabled()) {
            return;
        }
        Instant now = Instant.now(clock);
        skipPendingReminders(appointment.getId(), "Appointment was rescheduled before this reminder was sent.");
        enqueue(appointment, NotificationType.RESCHEDULE, now,
                "RESCHEDULE:" + appointment.getId() + ":" + appointment.getRescheduleCount());
        enqueueReminder(appointment, now);
    }

    @Transactional
    public void skipPendingReminders(UUID appointmentId, String reason) {
        List<Notification> reminders = notificationRepository
                .findByAppointmentIdAndTypeAndStatus(appointmentId, NotificationType.REMINDER,
                        NotificationStatus.PENDING);
        for (Notification reminder : reminders) {
            reminder.skip(reason);
            notificationRepository.save(reminder);
        }
    }

    private void enqueueReminder(Appointment appointment, Instant now) {
        Instant reminderAt = appointment.getStartAt().minus(properties.notifications().reminderLeadTime());
        if (!reminderAt.isAfter(now.plusSeconds(30))) {
            return;
        }
        enqueue(appointment, NotificationType.REMINDER, reminderAt,
                "REMINDER:" + appointment.getId() + ":" + appointment.getStartAt().getEpochSecond());
    }

    private void enqueue(Appointment appointment, NotificationType type, Instant scheduledAt, String dedupeKey) {
        if (notificationRepository.existsByDedupeKey(dedupeKey)) {
            return;
        }
        Notification notification = Notification.create(
                appointment,
                type,
                appointment.getCustomerEmail(),
                scheduledAt,
                properties.notifications().maxAttempts(),
                dedupeKey
        );
        notificationRepository.save(notification);
    }

    public static Duration backoff(int attempts) {
        long minutes = Math.min(60L, 1L << Math.min(attempts, 6));
        return Duration.ofMinutes(minutes);
    }
}
