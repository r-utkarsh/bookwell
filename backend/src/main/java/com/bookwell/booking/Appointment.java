package com.bookwell.booking;

import com.bookwell.common.persistence.AuditedEntity;
import com.bookwell.provider.ProviderProfile;
import com.bookwell.provider.ServiceOffering;
import com.bookwell.user.User;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends AuditedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ProviderProfile provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceOffering service;

    @Column(name = "start_ts", nullable = false)
    private Instant startAt;

    @Column(name = "end_ts", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @Column(name = "service_name", nullable = false, length = 140)
    private String serviceName;

    @Column(name = "service_duration_minutes", nullable = false)
    private int serviceDurationMinutes;

    @Column(name = "service_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal servicePrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "customer_notes", length = 1000)
    private String customerNotes;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private AppointmentActor cancelledBy;

    @Column(name = "rescheduled_from_ts")
    private Instant rescheduledFromAt;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

    public static Appointment create(ProviderProfile provider, User customer, ServiceOffering service,
                                     Instant startAt, Instant endAt, String notes) {
        Appointment appointment = new Appointment();
        appointment.id = UUID.randomUUID();
        appointment.provider = provider;
        appointment.customer = customer;
        appointment.service = service;
        appointment.startAt = startAt;
        appointment.endAt = endAt;
        appointment.status = AppointmentStatus.CONFIRMED;
        appointment.serviceName = service.getName();
        appointment.serviceDurationMinutes = service.getDurationMinutes();
        appointment.servicePrice = service.getPrice();
        appointment.currency = service.getCurrency();
        appointment.customerName = customer.getFullName();
        appointment.customerEmail = customer.getEmail();
        appointment.customerPhone = customer.getPhone();
        appointment.customerNotes = notes;
        return appointment;
    }

    public void cancel(AppointmentActor actor, String reason, Instant now) {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be cancelled.");
        }
        status = AppointmentStatus.CANCELLED;
        cancelledBy = actor;
        cancelledAt = now;
        cancellationReason = reason;
    }

    public void reschedule(Instant newStart, Instant newEnd) {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be rescheduled.");
        }
        rescheduledFromAt = startAt;
        startAt = newStart;
        endAt = newEnd;
        rescheduleCount++;
    }

    public void markCompleted() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be completed.");
        }
        status = AppointmentStatus.COMPLETED;
    }

    public void markNoShow() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be marked as no-show.");
        }
        status = AppointmentStatus.NO_SHOW;
    }
}
