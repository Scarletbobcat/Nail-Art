package com.nail_art.appointment_book.dtos;

import java.util.UUID;

public record CreateUserRequest(
        String username,
        String email,
        String password,
        String role,
        UUID organizationId
) {
}
