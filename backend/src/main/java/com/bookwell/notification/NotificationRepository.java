package com.bookwell.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByDedupeKey(String dedupeKey);

    boolean existsByDedupeKey(String dedupeKey);

    List<Notification> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(NotificationStatus status,
                                                                                   Instant now,
                                                                                   Pageable pageable);

    List<Notification> findByStatusAndUpdatedAtBefore(NotificationStatus status, Instant cutoff);

    List<Notification> findByAppointmentIdAndTypeAndStatus(UUID appointmentId,
                                                           NotificationType type,
                                                           NotificationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Notification n where n.id = :id")
    Optional<Notification> findForUpdate(@Param("id") UUID id);

    List<Notification> findByAppointmentIdOrderByCreatedAtAsc(UUID appointmentId);

    long countByStatus(NotificationStatus status);

    long countByStatusAndType(NotificationStatus status, NotificationType type);

    @Query("""
            select n from Notification n
            where (:status is null or n.status = :status)
              and (:type is null or n.type = :type)
            """)
    Page<Notification> searchAdmin(@Param("status") NotificationStatus status,
                                   @Param("type") NotificationType type,
                                   Pageable pageable);
}
