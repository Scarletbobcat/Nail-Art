package com.nail_art.appointment_book.services;

public interface SmsDeliveryGateway {
    enum Result {
        SENT,
        UNSUBSCRIBED,
        FAILED
    }

    Result sendReminder(String to, String message);
}
