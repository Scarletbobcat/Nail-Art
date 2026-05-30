package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.dtos.OrganizationSettingsUpdateRequest;
import com.nail_art.appointment_book.responses.OrganizationSettingsResponse;
import com.nail_art.appointment_book.security.AuthenticatedPrincipal;
import com.nail_art.appointment_book.services.OrganizationService;
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

@RequestMapping("/organization")
@RestController
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<OrganizationSettingsResponse> getSettings() {
        return ResponseEntity.ok(organizationService.getSettings(currentPrincipal()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<OrganizationSettingsResponse> updateSettings(
            @RequestBody OrganizationSettingsUpdateRequest request) {
        return ResponseEntity.ok(organizationService.updateSettings(currentPrincipal(), request));
    }

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BadCredentialsException("User is not authenticated");
        }
        return principal;
    }
}
