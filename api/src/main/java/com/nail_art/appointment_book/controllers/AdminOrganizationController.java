package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.AdminTwilioConfigRequest;
import com.nail_art.appointment_book.dtos.CreateOrganizationRequest;
import com.nail_art.appointment_book.dtos.OrganizationSettingsUpdateRequest;
import com.nail_art.appointment_book.responses.AdminOrganizationSummaryResponse;
import com.nail_art.appointment_book.responses.AdminTwilioConfigResponse;
import com.nail_art.appointment_book.responses.CreateOrganizationResponse;
import com.nail_art.appointment_book.responses.OrganizationSettingsResponse;
import com.nail_art.appointment_book.services.AdminProvisioningService;
import com.nail_art.appointment_book.services.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Platform-admin operator console. Every endpoint is gated to platform admins
 * (the org-less is_platform_admin capability), never owners/staff. Operates on
 * any salon by org id — these are non-@TenantId config tables, so an admin reads
 * and writes them directly without impersonation.
 */
@RequestMapping("/admin/organizations")
@RestController
@PreAuthorize("hasAuthority('platform_admin')")
public class AdminOrganizationController {
    private final OrganizationService organizationService;
    private final AdminProvisioningService adminProvisioningService;

    public AdminOrganizationController(
            OrganizationService organizationService,
            AdminProvisioningService adminProvisioningService
    ) {
        this.organizationService = organizationService;
        this.adminProvisioningService = adminProvisioningService;
    }

    @GetMapping
    public ResponseEntity<List<AdminOrganizationSummaryResponse>> listSalons() {
        return ResponseEntity.ok(organizationService.listAllSalons());
    }

    @PostMapping
    public ResponseEntity<CreateOrganizationResponse> createSalon(@RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminProvisioningService.createOrganization(request));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<OrganizationSettingsResponse> getSalon(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(organizationService.getSettings(organizationId));
    }

    @PutMapping("/{organizationId}")
    public ResponseEntity<OrganizationSettingsResponse> updateSalon(
            @PathVariable UUID organizationId,
            @RequestBody OrganizationSettingsUpdateRequest request) {
        return ResponseEntity.ok(organizationService.updateSettings(organizationId, request));
    }

    @GetMapping("/{organizationId}/twilio")
    public ResponseEntity<AdminTwilioConfigResponse> getTwilio(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(organizationService.getTwilioConfig(organizationId));
    }

    @PutMapping("/{organizationId}/twilio")
    public ResponseEntity<AdminTwilioConfigResponse> updateTwilio(
            @PathVariable UUID organizationId,
            @RequestBody AdminTwilioConfigRequest request) {
        return ResponseEntity.ok(organizationService.updateTwilioConfig(organizationId, request));
    }
}
