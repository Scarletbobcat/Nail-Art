package com.nail_art.appointment_book.responses;

/**
 * Owner Settings view. The auth token is write-only over the API: it is never
 * serialized here, neither as plaintext nor ciphertext. The caller sees only
 * whether Twilio is fully configured plus a masked sending number.
 */
public record OrganizationSettingsResponse(
        String name,
        String businessPhone,
        String timezone,
        boolean smsRemindersEnabled,
        boolean twilioConfigured,
        String twilioAccountSid,
        String twilioPhoneNumberMasked
) {
}
