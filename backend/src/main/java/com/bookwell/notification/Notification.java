package com.bookwell.notification;

import com.bookwell.booking.Appointment;
import com.bookwell.common.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivery_mode", length = 20)
    private String deliveryMode;

    @Column(name = "dedupe_key", nullable = false, length = 160)
    private String dedupeKey;

    public static Notification create(Appointment appointment, NotificationType type, String recipientEmail,
                                      Instant scheduledAt, int maxAttempts, String dedupeKey) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.appointment = appointment;
        notification.type = type;
        notification.recipientEmail = recipientEmail;
        notification.scheduledAt = scheduledAt;
        notification.maxAttempts = maxAttempts;
        notification.dedupeKey = dedupeKey;
        notification.status = NotificationStatus.PENDING;
        return notification;
    }

    public void beginAttempt() {
        attempts++;
        status = NotificationStatus.SENDING;
    }

    public void markSent(String deliveryMode, Instant now) {
        status = NotificationStatus.SENT;
        sentAt = now;
        lastError = null;
        this.deliveryMode = deliveryMode;
    }

    public void markFailure(String error, Instant nextAttemptAt, boolean finalFailure) {
        lastError = truncate(error);
        if (finalFailure) {
            status = NotificationStatus.FAILED;
        } else {
            status = NotificationStatus.PENDING;
            scheduledAt = nextAttemptAt;
        }
    }

    public void skip(String reason) {
        if (status == NotificationStatus.SENT) {
            return;
        }
        status = NotificationStatus.SKIPPED;
        lastError = truncate(reason);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
