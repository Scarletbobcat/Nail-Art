package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.OrganizationSettingsUpdateRequest;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import com.nail_art.appointment_book.responses.OrganizationSettingsResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationSettingsRepository organizationSettingsRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse getSettings(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));
        OrganizationSettings settings = organizationSettingsRepository.findById(organizationId).orElse(null);
        return toResponse(organization, settings, isConfigured(organizationId, settings));
    }

    /**
     * Update profile (name, business phone, timezone) and the SMS toggle for one
     * org. Shared by the owner Settings endpoint (scoped to the caller's own org)
     * and the platform-admin console (scoped to a target org id). Twilio
     * credentials are not touched here. The toggle can only be turned on when
     * Twilio is already configured (a hard 400); a profile-only edit leaves the
     * stored flag untouched, so a salon's reminders survive until creds are set.
     */
    @Transactional
    public OrganizationSettingsResponse updateSettings(
            UUID organizationId,
            OrganizationSettingsUpdateRequest request
    ) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));
        OrganizationSettings settings = organizationSettingsRepository.findById(organizationId)
                .orElseGet(() -> {
                    OrganizationSettings created = new OrganizationSettings();
                    created.setOrganizationId(organizationId);
                    return created;
                });

        if (isPresent(request.name())) {
            organization.setName(request.name());
        }
        if (request.businessPhone() != null) {
            organization.setBusinessPhone(request.businessPhone());
        }
        if (isPresent(request.timezone())) {
            organization.setTimezone(request.timezone());
        }

        boolean configured = isConfigured(organizationId, settings);
        Boolean requestedEnabled = request.smsRemindersEnabled();
        if (requestedEnabled != null) {
            if (requestedEnabled && !configured) {
                throw new IllegalArgumentException(
                        "SMS reminders can't be enabled until Twilio is configured for this salon");
            }
            settings.setSmsRemindersEnabled(requestedEnabled);
        }

        organizationRepository.save(organization);
        organizationSettingsRepository.save(settings);

        return toResponse(organization, settings, configured);
    }

    private boolean isConfigured(UUID organizationId, OrganizationSettings settings) {
        if (settings == null) {
            return false;
        }
        return isPresent(settings.getTwilioAccountSid())
                && isPresent(settings.getTwilioPhoneNumber())
                && Boolean.TRUE.equals(organizationSettingsRepository.authTokenPresent(organizationId));
    }

    private OrganizationSettingsResponse toResponse(
            Organization organization, OrganizationSettings settings, boolean configured) {
        return new OrganizationSettingsResponse(
                organization.getName(),
                organization.getBusinessPhone(),
                organization.getTimezone(),
                settings != null && settings.isSmsRemindersEnabled(),
                configured
        );
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
