package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.services.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SmsController {
    @Autowired
    private SmsService smsService;

    @PostMapping("/reminders")
    public ResponseEntity<?> sendReminders() {
        return smsService.sendReminders();
    }
}
