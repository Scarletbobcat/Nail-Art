package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByName(String name);

    default String currentTimezone() {
        UUID organizationId = TenantContext.get();
        if (organizationId == null) {
            return "America/New_York";
        }
        return findById(organizationId)
                .map(Organization::getTimezone)
                .orElse("America/New_York");
    }
}
