package com.bookwell.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @EntityGraph(attributePaths = {"provider", "service", "customer"})
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findDetailedById(@Param("id") UUID id);

    @Query("""
            select count(a) from Appointment a
            where a.customer.id = :customerId
              and a.status = 'CONFIRMED'
              and a.startAt > :now
            """)
    long countUpcomingConfirmedForCustomer(@Param("customerId") UUID customerId, @Param("now") Instant now);

    @Query("""
            select a from Appointment a
            where a.provider.id = :providerId
              and a.status in :statuses
              and a.startAt < :end
              and a.endAt > :start
            """)
    List<Appointment> findOverlapping(@Param("providerId") UUID providerId,
                                      @Param("statuses") Collection<AppointmentStatus> statuses,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    @EntityGraph(attributePaths = {"provider", "service"})
    @Query(value = """
            select a from Appointment a
            where a.customer.id = :customerId
              and (
                (:scope = 'upcoming' and a.status = 'CONFIRMED' and a.startAt >= :now)
                or
                (:scope = 'history' and (a.startAt < :now or a.status <> 'CONFIRMED'))
              )
            """,
            countQuery = """
                    select count(a) from Appointment a
                    where a.customer.id = :customerId
                      and (
                        (:scope = 'upcoming' and a.status = 'CONFIRMED' and a.startAt >= :now)
                        or
                        (:scope = 'history' and (a.startAt < :now or a.status <> 'CONFIRMED'))
                      )
                    """)
    Page<Appointment> findForCustomer(@Param("customerId") UUID customerId,
                                      @Param("scope") String scope,
                                      @Param("now") Instant now,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "service", "provider"})
    @Query("""
            select a from Appointment a
            where a.provider.id = :providerId
              and a.startAt >= :from
              and a.startAt < :to
              and (:status is null or a.status = :status)
            """)
    List<Appointment> findForProviderInRange(@Param("providerId") UUID providerId,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to,
                                             @Param("status") AppointmentStatus status);

    @EntityGraph(attributePaths = {"customer", "service"})
    @Query(value = """
            select a from Appointment a
            where a.provider.id = :providerId
              and (:status is null or a.status = :status)
              and a.startAt >= :from
              and a.startAt < :to
            """,
            countQuery = """
                    select count(a) from Appointment a
                    where a.provider.id = :providerId
                      and (:status is null or a.status = :status)
                      and a.startAt >= :from
                      and a.startAt < :to
                    """)
    Page<Appointment> findForProvider(@Param("providerId") UUID providerId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to,
                                      @Param("status") AppointmentStatus status,
                                      Pageable pageable);

    @EntityGraph(attributePaths = {"provider", "customer", "service"})
    @Query(value = """
            select a from Appointment a
            where (:providerId is null or a.provider.id = :providerId)
              and (:customerId is null or a.customer.id = :customerId)
              and (:status is null or a.status = :status)
              and a.startAt >= :from
              and a.startAt < :to
            """,
            countQuery = """
                    select count(a) from Appointment a
                    where (:providerId is null or a.provider.id = :providerId)
                      and (:customerId is null or a.customer.id = :customerId)
                      and (:status is null or a.status = :status)
                      and a.startAt >= :from
                      and a.startAt < :to
                    """)
    Page<Appointment> searchAdmin(@Param("providerId") UUID providerId,
                                  @Param("customerId") UUID customerId,
                                  @Param("status") AppointmentStatus status,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    long countByStatus(AppointmentStatus status);

    @Query("select count(a) from Appointment a where a.startAt >= :from and a.startAt < :to and a.status = 'CONFIRMED'")
    long countConfirmedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select count(a) from Appointment a where a.provider.id = :providerId and a.startAt >= :from and a.startAt < :to and a.status = 'CONFIRMED'")
    long countConfirmedForProviderBetween(@Param("providerId") UUID providerId,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    long countByProviderId(UUID providerId);

    long countByProviderIdAndStatus(UUID providerId, AppointmentStatus status);

    @Query("""
            select a from Appointment a
            where a.provider.id = :providerId
              and a.status = 'CONFIRMED'
              and a.startAt >= :now
            order by a.startAt asc
            """)
    List<Appointment> findConfirmedFuture(@Param("providerId") UUID providerId, @Param("now") Instant now);
}
