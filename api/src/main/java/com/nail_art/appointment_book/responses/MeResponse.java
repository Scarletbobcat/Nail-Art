package com.nail_art.appointment_book.responses;

import java.util.UUID;

/**
 * Identity payload for the current session. For a platform admin, {@code role} is
 * null, {@code isPlatformAdmin} is true, and {@code organization} is null (org-less).
 * For owners/staff, {@code role} is set, {@code isPlatformAdmin} is false, and
 * {@code organization} is populated.
 */
public record MeResponse(UserSummary user, OrganizationSummary organization) {
    public record UserSummary(UUID id, String username, String role, boolean isPlatformAdmin) {
    }

    public record OrganizationSummary(UUID id, String name, String timezone, String businessPhone) {
    }
}
