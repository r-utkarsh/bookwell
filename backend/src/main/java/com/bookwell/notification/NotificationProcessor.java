package com.bookwell.notification;

import com.bookwell.booking.Appointment;
import com.bookwell.booking.AppointmentRepository;
import com.bookwell.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final NotificationMailComposer composer;
    private final AppProperties properties;
    private final Clock clock;

    public NotificationProcessor(NotificationRepository notificationRepository,
                                 AppointmentRepository appointmentRepository,
                                 NotificationMailComposer composer,
                                 AppProperties properties,
                                 Clock clock) {
        this.notificationRepository = notificationRepository;
        this.appointmentRepository = appointmentRepository;
        this.composer = composer;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedNotification claim(UUID notificationId) {
        Notification notification = notificationRepository.findForUpdate(notificationId).orElse(null);
        if (notification == null || notification.getStatus() != NotificationStatus.PENDING) {
            return null;
        }
        Instant now = Instant.now(clock);
        if (notification.getScheduledAt().isAfter(now)) {
            return null;
        }
        if (notification.getAttempts() >= notification.getMaxAttempts()) {
            notification.markFailure("Retry limit reached before delivery.", now, true);
            notificationRepository.save(notification);
            return null;
        }
        notification.beginAttempt();
        notificationRepository.save(notification);
        Appointment appointment = appointmentRepository.findDetailedById(notification.getAppointment().getId())
                .orElseThrow(() -> new IllegalStateException("Notification appointment is missing."));
        NotificationMailComposer.EmailMessage message = composer.compose(notification.getType(), appointment);
        return new ClaimedNotification(notification.getId(), message);
    }

    @Transactional
    public void markSent(UUID notificationId, String deliveryMode) {
        notificationRepository.findForUpdate(notificationId).ifPresent(notification -> {
            if (notification.getStatus() == NotificationStatus.SENDING) {
                notification.markSent(deliveryMode, Instant.now(clock));
                notificationRepository.save(notification);
            }
        });
    }

    @Transactional
    public void markFailed(UUID notificationId, String error) {
        notificationRepository.findForUpdate(notificationId).ifPresent(notification -> {
            if (notification.getStatus() != NotificationStatus.SENDING) {
                return;
            }
            Instant now = Instant.now(clock);
            boolean finalFailure = notification.getAttempts() >= notification.getMaxAttempts();
            notification.markFailure(error,
                    now.plus(NotificationService.backoff(notification.getAttempts())),
                    finalFailure);
            notificationRepository.save(notification);
        });
    }

    /**
     * Recovers notifications abandoned by a crashed worker. Sending is at-least-once;
     * a crash after SMTP delivery but before markSent can result in a duplicate send.
     */
    @Transactional
    public int recoverStale() {
        Instant cutoff = Instant.now(clock).minusSeconds(600);
        List<Notification> stale = notificationRepository
                .findByStatusAndUpdatedAtBefore(NotificationStatus.SENDING, cutoff);
        for (Notification notification : stale) {
            if (notification.getAttempts() >= notification.getMaxAttempts()) {
                notification.markFailure("Worker stopped before delivery was confirmed.", Instant.now(clock), true);
            } else {
                notification.setStatus(NotificationStatus.PENDING);
                notification.setScheduledAt(Instant.now(clock));
            }
        }
        if (!stale.isEmpty()) {
            notificationRepository.saveAll(stale);
        }
        return stale.size();
    }

    public record ClaimedNotification(UUID id, NotificationMailComposer.EmailMessage message) {
    }
}
