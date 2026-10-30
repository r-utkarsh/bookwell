package com.bookwell.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, UUID> {

    List<ScheduleException> findByProviderIdAndDateOrderByStartTimeAsc(UUID providerId, LocalDate date);

    List<ScheduleException> findByProviderIdAndDateBetweenOrderByDateAscStartTimeAsc(UUID providerId,
                                                                                     LocalDate from,
                                                                                     LocalDate to);

    List<ScheduleException> findByProviderIdAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(UUID providerId,
                                                                                              LocalDate from);

    Optional<ScheduleException> findByIdAndProviderId(UUID id, UUID providerId);

    boolean existsByProviderIdAndDateAndType(UUID providerId, LocalDate date, ScheduleExceptionType type);
}
