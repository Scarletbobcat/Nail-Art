package com.nail_art.appointment_book.dtos;

/**
 * Platform-admin update of a salon user's login credentials. Both fields optional:
 * a blank/null field is left unchanged. {@code username} is unique (citext); a
 * collision returns 409. {@code password} is hashed; it is write-only and never read back.
 */
public record AdminUserUpdateRequest(
        String username,
        String password
) {
}
