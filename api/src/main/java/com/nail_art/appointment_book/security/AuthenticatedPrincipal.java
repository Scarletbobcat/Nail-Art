package com.nail_art.appointment_book.security;

import java.util.UUID;

public record AuthenticatedPrincipal(UUID userId, UUID organizationId, String role) {
}
