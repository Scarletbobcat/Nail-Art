package com.nail_art.appointment_book.dtos;

/**
 * Platform-admin Twilio config write. {@code accountSid} and {@code phoneNumber}
 * are non-secret identifiers — set when non-blank, left untouched when blank.
 * {@code authToken} is the only secret: set/overwritten when non-blank, left
 * untouched when blank (so a profile-only edit never wipes a stored token). It is
 * write-only — never returned by any read.
 */
public record AdminTwilioConfigRequest(
        String accountSid,
        String phoneNumber,
        String authToken
) {
}
