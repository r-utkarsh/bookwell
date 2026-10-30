package com.bookwell.provider;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderProfileRepository extends JpaRepository<ProviderProfile, UUID> {

    Optional<ProviderProfile> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    @Query("select p from ProviderProfile p where p.id = :id")
    Optional<ProviderProfile> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "user")
    @Query("select p from ProviderProfile p where p.user.id = :userId")
    Optional<ProviderProfile> findDetailedByUserId(@Param("userId") UUID userId);

    long countByStatus(ProviderStatus status);

    @Query("""
            select p from ProviderProfile p
            where p.status = :status
              and (:q = '' or lower(p.displayName) like lower(concat('%', :q, '%'))
                   or lower(p.businessName) like lower(concat('%', :q, '%'))
                   or lower(p.category) like lower(concat('%', :q, '%'))
                   or lower(p.city) like lower(concat('%', :q, '%')))
              and (:category = '' or lower(p.category) = lower(:category))
            """)
    Page<ProviderProfile> searchPublic(@Param("status") ProviderStatus status,
                                       @Param("q") String q,
                                       @Param("category") String category,
                                       Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from ProviderProfile p
            where (:status is null or p.status = :status)
              and (:q = '' or lower(p.displayName) like lower(concat('%', :q, '%'))
                   or lower(p.businessName) like lower(concat('%', :q, '%'))
                   or lower(p.user.email) like lower(concat('%', :q, '%')))
            """)
    Page<ProviderProfile> searchAdmin(@Param("status") ProviderStatus status,
                                      @Param("q") String q,
                                      Pageable pageable);

    @Query("select distinct p.category from ProviderProfile p where p.status = 'APPROVED' order by p.category")
    List<String> findPublicCategories();

    @Query("""
            select s.provider.id, count(s.id), min(s.price)
            from ServiceOffering s
            where s.active = true and s.provider.id in :providerIds
            group by s.provider.id
            """)
    List<Object[]> summarizeActiveServices(@Param("providerIds") Collection<UUID> providerIds);
}
