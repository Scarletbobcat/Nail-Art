package com.nail_art.appointment_book.responses;

/**
 * Platform-admin Twilio config read. Exposes the non-secret identifiers and
 * whether the salon is fully configured. The auth token is write-only and is
 * NEVER included here.
 */
public record AdminTwilioConfigResponse(
        boolean configured,
        String accountSid,
        String phoneNumber
) {
}
