package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.twilio.Twilio;
import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    // Twilio error code 21610: recipient has unsubscribed / blocked sender.
    private static final int TWILIO_UNSUBSCRIBED_CODE = 21610;

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String phoneNumber;

    @Autowired
    private AppointmentService appointmentService;

    @Scheduled(cron = "0 0 10 * * *", zone = "America/New_York")
    public void sendReminders() {
        List<Appointment> appointments = appointmentService.getAppointmentsForTomorrow();
        int sent = 0;
        int skippedAlreadySent = 0;
        int skippedNoPhone = 0;
        int skippedUnsubscribed = 0;
        int failed = 0;

        for (Appointment appointment : appointments) {
            if (appointment.getReminderSent() != null && appointment.getReminderSent()) {
                skippedAlreadySent++;
                continue;
            }
            if (appointment.getPhoneNumber() == null || appointment.getPhoneNumber().isEmpty()) {
                skippedNoPhone++;
                continue;
            }

            SendOutcome outcome = sendWithRetry(appointment.getPhoneNumber(), buildReminderMessage(appointment));
            switch (outcome) {
                case SENT -> {
                    appointmentService.markReminderSent(appointment.getId());
                    sent++;
                }
                case UNSUBSCRIBED -> skippedUnsubscribed++;
                case FAILED -> failed++;
            }
        }

        log.info("Reminders run complete: sent={}, alreadySent={}, noPhone={}, unsubscribed={}, failed={}",
                sent, skippedAlreadySent, skippedNoPhone, skippedUnsubscribed, failed);
    }

    private String buildReminderMessage(Appointment appointment) {
        LocalDateTime date = LocalDateTime.parse(appointment.getDate() + appointment.getStartTime());
        String day = date.getDayOfWeek().toString();
        day = day.charAt(0) + day.substring(1).toLowerCase();
        return "Hello! This is Nail Art & Spa LLC., and this is a reminder for your appointment on "
                + day + ", "
                + date.format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))
                + ". Please call the salon at 330-758-6633 if you "
                + "need to reschedule or cancel. We look forward to seeing you!\n\n"
                + "Reply STOP to stop receiving messages from this number.";
    }

    private enum SendOutcome { SENT, UNSUBSCRIBED, FAILED }

    private SendOutcome sendWithRetry(String to, String message) {
        Twilio.init(accountSid, authToken);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Message.creator(new PhoneNumber(to), new PhoneNumber(phoneNumber), message).create();
                return SendOutcome.SENT;
            } catch (ApiException e) {
                if (e.getCode() != null && e.getCode() == TWILIO_UNSUBSCRIBED_CODE) {
                    log.info("Skipping unsubscribed recipient {}", maskPhone(to));
                    return SendOutcome.UNSUBSCRIBED;
                }
                Integer status = e.getStatusCode();
                boolean retryable = status != null && (status >= 500 || status == 429);
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    log.warn("Twilio API error sending to {} (code={}, status={}): {}",
                            maskPhone(to), e.getCode(), status, e.getMessage());
                    return SendOutcome.FAILED;
                }
                log.warn("Transient Twilio error (status={}), retrying attempt {}/{}",
                        status, attempt + 1, MAX_ATTEMPTS);
            } catch (ApiConnectionException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Network error sending to {} after {} attempts: {}",
                            maskPhone(to), MAX_ATTEMPTS, e.getMessage());
                    return SendOutcome.FAILED;
                }
                log.warn("Network error, retrying attempt {}/{}", attempt + 1, MAX_ATTEMPTS);
            } catch (Exception e) {
                log.warn("Unexpected error sending to {}: {}", maskPhone(to), e.getMessage());
                return SendOutcome.FAILED;
            }
            try {
                Thread.sleep(RETRY_BACKOFF_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return SendOutcome.FAILED;
            }
        }
        return SendOutcome.FAILED;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
