package com.bookwell.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule, UUID> {

    List<WeeklySchedule> findByProviderIdOrderByDayOfWeekAscStartTimeAsc(UUID providerId);

    void deleteByProviderId(UUID providerId);
}
