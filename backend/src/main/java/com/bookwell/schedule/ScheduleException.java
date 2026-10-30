package com.bookwell.schedule;

import com.bookwell.common.persistence.AuditedEntity;
import com.bookwell.provider.ProviderProfile;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "schedule_exceptions")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleException extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderProfile provider;

    @Column(name = "exception_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ScheduleExceptionType type;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 240)
    private String reason;

    public static ScheduleException create(ProviderProfile provider, LocalDate date, ScheduleExceptionType type,
                                           LocalTime start, LocalTime end, String reason) {
        ScheduleException exception = new ScheduleException();
        exception.id = UUID.randomUUID();
        exception.provider = provider;
        exception.date = date;
        exception.type = type;
        exception.startTime = start;
        exception.endTime = end;
        exception.reason = reason;
        return exception;
    }
}
