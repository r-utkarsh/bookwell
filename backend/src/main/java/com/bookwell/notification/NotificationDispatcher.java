package com.bookwell.notification;

import com.bookwell.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int BATCH_SIZE = 25;

    private final NotificationRepository notificationRepository;
    private final NotificationProcessor processor;
    private final MailGateway mailGateway;
    private final AppProperties properties;
    private final Clock clock;

    public NotificationDispatcher(NotificationRepository notificationRepository,
                                  NotificationProcessor processor,
                                  MailGateway mailGateway,
                                  AppProperties properties,
                                  Clock clock) {
        this.notificationRepository = notificationRepository;
        this.processor = processor;
        this.mailGateway = mailGateway;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-interval-ms:30000}", initialDelayString = "20000")
    public void processPending() {
        if (!properties.notifications().enabled() || !properties.notifications().workerEnabled()) {
            return;
        }
        try {
            int recovered = processor.recoverStale();
            if (recovered > 0) {
                log.warn("Recovered {} stale notification claim(s).", recovered);
            }
            List<Notification> due = notificationRepository
                    .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                            NotificationStatus.PENDING, Instant.now(clock), PageRequest.of(0, BATCH_SIZE));
            for (Notification notification : due) {
                NotificationProcessor.ClaimedNotification claimed;
                try {
                    claimed = processor.claim(notification.getId());
                } catch (Exception ex) {
                    log.error("Could not prepare notification {}", notification.getId(), ex);
                    processor.markFailed(notification.getId(), "Notification preparation failed.");
                    continue;
                }
                if (claimed == null) {
                    continue;
                }
                try {
                    MailGateway.DeliveryResult result = mailGateway.send(claimed.message());
                    if (result.success()) {
                        processor.markSent(claimed.id(), result.mode());
                    } else {
                        processor.markFailed(claimed.id(), result.error());
                        log.warn("Mail delivery failed for notification {}: {}", claimed.id(), result.error());
                    }
                } catch (Exception ex) {
                    processor.markFailed(claimed.id(), ex.getMessage());
                    log.warn("Mail delivery threw for notification {}", claimed.id(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("Notification worker run failed", ex);
        }
    }
}
