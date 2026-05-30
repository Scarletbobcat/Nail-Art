package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.dtos.OrganizationSettingsUpdateRequest;
import com.nail_art.appointment_book.entities.Organization;
import com.nail_art.appointment_book.entities.OrganizationSettings;
import com.nail_art.appointment_book.repositories.OrganizationRepository;
import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import com.nail_art.appointment_book.responses.OrganizationSettingsResponse;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final TwilioCredentialsService twilioCredentialsService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationSettingsRepository organizationSettingsRepository,
            TwilioCredentialsService twilioCredentialsService
    ) {
        this.organizationRepository = organizationRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.twilioCredentialsService = twilioCredentialsService;
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse getSettings(AuthenticatedPrincipal principal) {
        UUID organizationId = principal.organizationId();
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));
        OrganizationSettings settings = organizationSettingsRepository.findById(organizationId).orElse(null);
        return toResponse(organization, settings, tokenPresent(organizationId));
    }

    /**
     * One transaction across Organization (profile), OrganizationSettings (plaintext
     * Twilio fields + sms flag), and the native token upsert, so the enable-gate
     * validates the final committed state and no half-written state is observable.
     */
    @Transactional
    public OrganizationSettingsResponse updateSettings(
            AuthenticatedPrincipal principal,
            OrganizationSettingsUpdateRequest request
    ) {
        UUID organizationId = principal.organizationId();
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BadCredentialsException("Organization not found"));
        OrganizationSettings settings = organizationSettingsRepository.findById(organizationId)
                .orElseGet(() -> {
                    OrganizationSettings created = new OrganizationSettings();
                    created.setOrganizationId(organizationId);
                    return created;
                });

        // Profile (NOT NULL columns only set when a real value is supplied).
        if (isPresent(request.name())) {
            organization.setName(request.name());
        }
        if (request.businessPhone() != null) {
            organization.setBusinessPhone(request.businessPhone());
        }
        if (isPresent(request.timezone())) {
            organization.setTimezone(request.timezone());
        }

        // Twilio identifiers: null = leave untouched; an explicit empty value clears
        // (so a config can be deliberately blanked, which then auto-disables below).
        if (request.twilioAccountSid() != null) {
            settings.setTwilioAccountSid(request.twilioAccountSid());
        }
        if (request.twilioPhoneNumber() != null) {
            settings.setTwilioPhoneNumber(request.twilioPhoneNumber());
        }

        boolean tokenProvided = isPresent(request.twilioAuthToken());
        boolean tokenPresentAfter = tokenProvided || tokenPresent(organizationId);

        // Completeness is evaluated against the POST-write state.
        boolean complete = isPresent(settings.getTwilioAccountSid())
                && isPresent(settings.getTwilioPhoneNumber())
                && tokenPresentAfter;

        boolean currentlyEnabled = settings.isSmsRemindersEnabled();
        Boolean requestedEnabled = request.smsRemindersEnabled();
        boolean wantEnabled = requestedEnabled != null ? requestedEnabled : currentlyEnabled;
        if (wantEnabled && !complete) {
            if (Boolean.TRUE.equals(requestedEnabled)) {
                // Explicitly enabling with incomplete config is a hard 400.
                throw new IllegalArgumentException(
                        "SMS reminders require Account SID, Auth Token, and Phone Number to be set");
            }
            // A partial edit that happens to leave config incomplete auto-disables the
            // toggle instead of silently leaving it on — the bad state is unreachable.
            wantEnabled = false;
        }
        settings.setSmsRemindersEnabled(wantEnabled);

        organizationRepository.save(organization);
        organizationSettingsRepository.save(settings);
        if (tokenProvided) {
            twilioCredentialsService.saveAuthToken(organizationId, request.twilioAuthToken());
        }

        return toResponse(organization, settings, tokenPresentAfter);
    }

    private boolean tokenPresent(UUID organizationId) {
        return Boolean.TRUE.equals(organizationSettingsRepository.authTokenPresent(organizationId));
    }

    private OrganizationSettingsResponse toResponse(
            Organization organization, OrganizationSettings settings, boolean tokenPresent) {
        String sid = settings == null ? null : settings.getTwilioAccountSid();
        String phone = settings == null ? null : settings.getTwilioPhoneNumber();
        boolean smsEnabled = settings != null && settings.isSmsRemindersEnabled();
        boolean configured = isPresent(sid) && isPresent(phone) && tokenPresent;
        return new OrganizationSettingsResponse(
                organization.getName(),
                organization.getBusinessPhone(),
                organization.getTimezone(),
                smsEnabled,
                configured,
                sid,
                maskPhone(phone)
        );
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (trimmed.length() <= 4) {
            return "••••";
        }
        return "•••• " + trimmed.substring(trimmed.length() - 4);
    }
}
