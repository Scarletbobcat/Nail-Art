package com.nail_art.appointment_book.responses;

import java.util.UUID;

/** Result of provisioning a salon: the new org and its first owner login. */
public record CreateOrganizationResponse(
        UUID organizationId,
        String name,
        UUID ownerUserId,
        String ownerUsername
) {
}
