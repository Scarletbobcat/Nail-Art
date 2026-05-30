package com.nail_art.appointment_book.dtos;

/**
 * Owner Settings update. Every field is optional: a {@code null} field leaves the
 * stored value untouched (symmetric for token, sid, and phone), so a profile-only
 * edit never wipes Twilio credentials. The auth token is additionally never written
 * when blank. {@code smsRemindersEnabled} is a {@link Boolean} so "not provided"
 * (null) is distinguishable from an explicit false.
 */
public record OrganizationSettingsUpdateRequest(
        String name,
        String businessPhone,
        String timezone,
        Boolean smsRemindersEnabled,
        String twilioAccountSid,
        String twilioAuthToken,
        String twilioPhoneNumber
) {
}
