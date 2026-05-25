package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.OrganizationUser;
import com.nail_art.appointment_book.entities.OrganizationUserId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, OrganizationUserId> {
    boolean existsByIdUserIdAndIdOrganizationId(UUID userId, UUID organizationId);

    Optional<OrganizationUser> findFirstByIdUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<OrganizationUser> findByIdUserIdAndIdOrganizationId(UUID userId, UUID organizationId);

    default boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId) {
        return existsByIdUserIdAndIdOrganizationId(userId, organizationId);
    }

    default Optional<OrganizationUser> findFirstByUserId(UUID userId) {
        return findFirstByIdUserIdOrderByCreatedAtAsc(userId);
    }

    default Optional<OrganizationUser> findByUserIdAndOrganizationId(UUID userId, UUID organizationId) {
        return findByIdUserIdAndIdOrganizationId(userId, organizationId);
    }
}
