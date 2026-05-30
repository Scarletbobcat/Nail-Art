package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    @Query("select s from Service s where s.id = :id")
    Optional<Service> findScopedById(UUID id);

    /**
     * Insert an org's "Unavailable" marker service with an explicit organization_id.
     * Native (not @TenantId-stamped) so it runs correctly inside the platform-admin
     * create-salon transaction, whose Hibernate session is bound to the sentinel
     * tenant — mirrors create_organization.py's raw insert.
     */
    @Modifying
    @Query(value = "insert into services (organization_id, name, is_unavailability_marker) "
            + "values (:organizationId, :name, true)", nativeQuery = true)
    void insertUnavailabilityMarker(@Param("organizationId") UUID organizationId, @Param("name") String name);

    List<Service> findByNameContainingIgnoreCase(String name);

    Page<Service> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
