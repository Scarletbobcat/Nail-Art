package com.nail_art.appointment_book.responses;

import java.util.UUID;

/** One user (owner or staff) within a salon, for the platform-admin console. */
public record AdminUserSummary(UUID id, String username, String role) {
}
