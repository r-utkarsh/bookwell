package com.bookwell.provider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    List<ServiceOffering> findByProviderIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID providerId);

    List<ServiceOffering> findByProviderIdOrderBySortOrderAscNameAsc(UUID providerId);

    Optional<ServiceOffering> findByIdAndProviderId(UUID id, UUID providerId);

    long countByProviderIdAndActiveTrue(UUID providerId);

    @Query("select min(s.price) from ServiceOffering s where s.provider.id = :providerId and s.active = true")
    BigDecimal findMinActivePrice(@Param("providerId") UUID providerId);
}
