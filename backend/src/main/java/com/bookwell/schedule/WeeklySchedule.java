package com.bookwell.schedule;

import com.bookwell.common.persistence.AuditedEntity;
import com.bookwell.provider.ProviderProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "weekly_schedules")
@Getter
@Setter
@NoArgsConstructor
public class WeeklySchedule extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderProfile provider;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public static WeeklySchedule create(ProviderProfile provider, int dayOfWeek, LocalTime start, LocalTime end) {
        WeeklySchedule schedule = new WeeklySchedule();
        schedule.id = UUID.randomUUID();
        schedule.provider = provider;
        schedule.dayOfWeek = (short) dayOfWeek;
        schedule.startTime = start;
        schedule.endTime = end;
        return schedule;
    }
}
