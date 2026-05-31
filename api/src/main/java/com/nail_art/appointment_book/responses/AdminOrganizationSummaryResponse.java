package com.nail_art.appointment_book.responses;

import java.util.UUID;

/** One salon row in the platform-admin "all salons" list. Never carries Twilio secrets. */
public record AdminOrganizationSummaryResponse(
        UUID id,
        String name,
        String timezone,
        String businessPhone,
        boolean smsRemindersEnabled,
        boolean twilioConfigured
) {
}
