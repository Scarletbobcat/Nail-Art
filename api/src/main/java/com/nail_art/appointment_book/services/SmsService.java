package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SmsService {
    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String phoneNumber;

    @Autowired
    private AppointmentService appointmentService;

    private ResponseEntity<?> sendSms(String to, String message) {
        try {
            Twilio.init(accountSid, authToken);
            Message.creator(new PhoneNumber(to), new PhoneNumber(phoneNumber), message).create();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send SMS: " + e.getMessage());
        }
    }

    public ResponseEntity<?> sendReminders() {
        List<Appointment> appointments = appointmentService.getAppointmentsNextWorkDay();
        for (Appointment appointment : appointments) {
            // skips appointment if reminder has already been sent
            if ((appointment.getReminderSent() != null && appointment.getReminderSent())
                    || appointment.getPhoneNumber() == null) {
                continue;
            }
            LocalDateTime date = LocalDateTime.parse(appointment.getDate() + appointment.getStartTime());
            String day = date.getDayOfWeek().toString();
            day = day.charAt(0) + day.substring(1).toLowerCase();
            String message = "Hello! This is a reminder for your appointment "
                    + day
                    + " at "
                    + date.format(DateTimeFormatter.ofPattern("h:mm a"))
                    + ". Please call the salon at 330-758-6633 if you " +
                    "need to reschedule or cancel. We look forward to seeing you!";
            ResponseEntity<?> response = sendSms(appointment.getPhoneNumber(), message);
            // sets reminderSent to true if SMS was sent successfully
            if (response.getStatusCode() == HttpStatus.OK) {
                appointment.setReminderSent(true);
                appointmentService.editAppointment(appointment);
            } else {
                return response;
            }
        }
        return ResponseEntity.ok().build();
    }
}
