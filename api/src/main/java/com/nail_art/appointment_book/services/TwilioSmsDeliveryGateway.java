package com.nail_art.appointment_book.services;

import com.twilio.exception.ApiConnectionException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TwilioSmsDeliveryGateway implements SmsDeliveryGateway {
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsDeliveryGateway.class);
    private static final int TWILIO_UNSUBSCRIBED_CODE = 21610;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    @Override
    public Result sendReminder(String to, String message, TwilioCredentials credentials) {
        // Per-request client built from this org's own credentials — never the
        // global static Twilio.init state, which would leak one org's account
        // into another's send across the scheduler loop.
        TwilioRestClient client = new TwilioRestClient.Builder(
                credentials.accountSid(), credentials.authToken()).build();
        PhoneNumber from = new PhoneNumber(credentials.phoneNumber());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Message.creator(new PhoneNumber(to), from, message).create(client);
                return Result.SENT;
            } catch (ApiException e) {
                if (e.getCode() != null && e.getCode() == TWILIO_UNSUBSCRIBED_CODE) {
                    log.info("Skipping unsubscribed recipient {}", maskPhone(to));
                    return Result.UNSUBSCRIBED;
                }
                Integer status = e.getStatusCode();
                boolean retryable = status != null && (status >= 500 || status == 429);
                if (!retryable || attempt == MAX_ATTEMPTS) {
                    log.warn("Twilio API error sending to {} (code={}, status={}): {}",
                            maskPhone(to), e.getCode(), status, e.getMessage());
                    return Result.FAILED;
                }
                log.warn("Transient Twilio error (status={}), retrying attempt {}/{}",
                        status, attempt + 1, MAX_ATTEMPTS);
            } catch (ApiConnectionException e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.warn("Network error sending to {} after {} attempts: {}",
                            maskPhone(to), MAX_ATTEMPTS, e.getMessage());
                    return Result.FAILED;
                }
                log.warn("Network error, retrying attempt {}/{}", attempt + 1, MAX_ATTEMPTS);
            } catch (Exception e) {
                log.warn("Unexpected error sending to {}: {}", maskPhone(to), e.getMessage());
                return Result.FAILED;
            }
            try {
                Thread.sleep(RETRY_BACKOFF_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return Result.FAILED;
            }
        }
        return Result.FAILED;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }
}
