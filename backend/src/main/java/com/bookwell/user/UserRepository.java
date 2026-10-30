package com.bookwell.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where u.role = :role
              and (:enabled is null or u.enabled = :enabled)
              and (:q = '' or lower(u.email) like lower(concat('%', :q, '%'))
                   or lower(u.fullName) like lower(concat('%', :q, '%')))
            """)
    Page<User> searchByRole(@Param("role") Role role, @Param("q") String q,
                            @Param("enabled") Boolean enabled, Pageable pageable);
}
