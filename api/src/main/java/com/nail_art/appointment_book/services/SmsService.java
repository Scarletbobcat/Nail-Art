package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    private void sendSms(String to, String message) {
        Twilio.init(accountSid, authToken);
        Message.creator(new PhoneNumber(to), new PhoneNumber(phoneNumber), message).create();
    }

    public void sendReminders() {
        List<Appointment> appointments = appointmentService.getAppointmentsNextWorkDay();
        for (Appointment appointment : appointments) {
            LocalDateTime date = LocalDateTime.parse(appointment.getDate() + appointment.getStartTime());
            String day = date.getDayOfWeek().toString();
            day = day.charAt(0) + day.substring(1).toLowerCase();
            String message = "Hello! This is a reminder for your appointment "
                    + day
                    + " at "
                    + date.format(DateTimeFormatter.ofPattern("h:mm a"))
                    + ". We look forward to seeing you!";
            sendSms(appointment.getPhoneNumber(), message);
        }
    }
}
