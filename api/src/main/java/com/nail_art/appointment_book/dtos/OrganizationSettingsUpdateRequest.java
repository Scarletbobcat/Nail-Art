package com.nail_art.appointment_book.dtos;

/**
 * Owner Settings update. Owners manage their salon profile and the SMS on/off
 * toggle only — Twilio credentials are operator-managed (via scripts), never set
 * here. Every field is optional: a {@code null} field leaves the stored value
 * untouched. {@code smsRemindersEnabled} is a {@link Boolean} so "not provided"
 * (null) is distinguishable from an explicit false.
 */
public record OrganizationSettingsUpdateRequest(
        String name,
        String businessPhone,
        String timezone,
        Boolean smsRemindersEnabled
) {
}
