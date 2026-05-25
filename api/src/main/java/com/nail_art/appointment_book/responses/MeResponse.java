package com.nail_art.appointment_book.responses;

import java.util.UUID;

public record MeResponse(UserSummary user, OrganizationSummary organization) {
    public record UserSummary(UUID id, String username, String role) {
    }

    public record OrganizationSummary(UUID id, String name, String timezone, String businessPhone) {
    }
}
