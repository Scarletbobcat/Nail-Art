package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.OrganizationSettingsUpdateRequest;
import com.nail_art.appointment_book.responses.OrganizationSettingsResponse;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import com.nail_art.appointment_book.services.OrganizationService;
import com.nail_art.appointment_book.services.ProductAnalytics;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RequestMapping("/organization")
@RestController
public class OrganizationController {
    private final OrganizationService organizationService;
    private final ProductAnalytics productAnalytics;

    public OrganizationController(OrganizationService organizationService, ProductAnalytics productAnalytics) {
        this.organizationService = organizationService;
        this.productAnalytics = productAnalytics;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<OrganizationSettingsResponse> getSettings() {
        return ResponseEntity.ok(organizationService.getSettings(currentPrincipal().organizationId()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<OrganizationSettingsResponse> updateSettings(
            @RequestBody OrganizationSettingsUpdateRequest request) {
        OrganizationSettingsResponse saved =
                organizationService.updateSettings(currentPrincipal().organizationId(), request);
        productAnalytics.capture("settings_saved", Map.of(
                "timezone", String.valueOf(saved.timezone()),
                "sms_reminders_enabled", saved.smsRemindersEnabled()));
        return ResponseEntity.ok(saved);
    }

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BadCredentialsException("User is not authenticated");
        }
        return principal;
    }
}
