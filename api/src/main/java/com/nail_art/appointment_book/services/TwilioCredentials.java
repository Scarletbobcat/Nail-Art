package com.nail_art.appointment_book.services;

/**
 * A single organization's Twilio sending credentials, assembled from
 * {@code organization_settings} with the auth token already decrypted.
 *
 * <p>{@link #toString()} redacts the token so the credentials cannot leak
 * through the SMS gateway's retry/skip log lines or an exception message.
 */
public record TwilioCredentials(String accountSid, String authToken, String phoneNumber) {

    /** True only when all three fields are present and non-blank. */
    public boolean isComplete() {
        return isPresent(accountSid) && isPresent(authToken) && isPresent(phoneNumber);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "TwilioCredentials{accountSid=" + accountSid
                + ", authToken=REDACTED"
                + ", phoneNumber=" + phoneNumber + "}";
    }
}
