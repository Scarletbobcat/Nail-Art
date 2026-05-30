package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.CreateOrganizationRequest;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import com.nail_art.appointment_book.repositories.ServiceRepository;
import com.nail_art.appointment_book.responses.CreateOrganizationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Platform-admin salon provisioning: create an organization, its settings row,
 * its first owner login, and the "Unavailable" marker service in one transaction
 * — the server-side equivalent of create_organization.py +
 * bootstrap_organization_owner.py. The owner is created through
 * {@link UserService} so password hashing and the duplicate-username guard match
 * the normal user-creation path; a failure anywhere rolls the whole salon back.
 */
@org.springframework.stereotype.Service
public class AdminProvisioningService {
    private static final String DEFAULT_TIMEZONE = "America/New_York";
    private static final String UNAVAILABILITY_MARKER_NAME = "Unavailable";

    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final ServiceRepository serviceRepository;
    private final UserService userService;

    public AdminProvisioningService(
            OrganizationRepository organizationRepository,
            OrganizationSettingsRepository organizationSettingsRepository,
            ServiceRepository serviceRepository,
            UserService userService
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.serviceRepository = serviceRepository;
        this.userService = userService;
    }

    @Transactional
    public CreateOrganizationResponse createOrganization(CreateOrganizationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String name = requireText(request.name(), "name");
        String ownerUsername = requireText(request.ownerUsername(), "ownerUsername");
        String ownerPassword = requireText(request.ownerPassword(), "ownerPassword");
        String timezone = isPresent(request.timezone()) ? request.timezone() : DEFAULT_TIMEZONE;

        organizationRepository.findByName(name).ifPresent(existing -> {
            throw new DataIntegrityViolationException("organization already exists: " + name);
        });

        Organization organization = new Organization();
        organization.setName(name);
        organization.setTimezone(timezone);
        organization.setBusinessPhone(request.businessPhone());
        Organization savedOrganization = organizationRepository.saveAndFlush(organization);
        UUID organizationId = savedOrganization.getId();

        OrganizationSettings settings = new OrganizationSettings();
        settings.setOrganizationId(organizationId);
        settings.setSmsRemindersEnabled(false);
        organizationSettingsRepository.save(settings);

        // The marker service is tenant-owned (@TenantId), but this transaction's
        // Hibernate session is bound to the sentinel tenant (the admin is org-less),
        // so a JPA save would stamp the wrong organization_id. Insert it natively
        // with an explicit org id instead — same transaction, correct FK.
        serviceRepository.insertUnavailabilityMarker(organizationId, UNAVAILABILITY_MARKER_NAME);

        User owner = userService.createUserInOrganization(
                organizationId, ownerUsername, null, ownerPassword, "owner");

        return new CreateOrganizationResponse(
                organizationId, savedOrganization.getName(), owner.getId(), owner.getUsername());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
