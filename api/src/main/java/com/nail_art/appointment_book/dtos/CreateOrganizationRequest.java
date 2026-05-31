package com.nail_art.appointment_book.dtos;

/**
 * Platform-admin create-salon request: a new organization plus its first owner
 * login, provisioned in one transaction. Timezone defaults to America/New_York
 * when blank; business phone is optional.
 */
public record CreateOrganizationRequest(
        String name,
        String timezone,
        String businessPhone,
        String ownerUsername,
        String ownerPassword
) {
}
