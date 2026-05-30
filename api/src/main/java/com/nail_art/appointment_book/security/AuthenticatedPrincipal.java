package com.nail_art.appointment_book.security;

import java.util.UUID;

/**
 * The authenticated caller. Two shapes:
 * <ul>
 *   <li>Org member (owner/staff): {@code organizationId} and {@code role} are set,
 *       {@code platformAdmin} is false.</li>
 *   <li>Platform admin: {@code organizationId} and {@code role} are null,
 *       {@code platformAdmin} is true. Org-less; authority comes from the flag.</li>
 * </ul>
 */
public record AuthenticatedPrincipal(UUID userId, UUID organizationId, String role, boolean platformAdmin) {
}
