package com.nail_art.appointment_book.responses;

/**
 * Owner Settings view. Twilio credentials are operator-managed and never exposed
 * here — the owner sees only whether Twilio is configured (so the SMS toggle
 * knows whether it can be enabled). {@code smsRemindersEnabled} is the stored
 * flag; callers should treat reminders as effectively on only when
 * {@code twilioConfigured} is also true.
 */
public record OrganizationSettingsResponse(
        String name,
        String businessPhone,
        String timezone,
        boolean smsRemindersEnabled,
        boolean twilioConfigured
) {
}
