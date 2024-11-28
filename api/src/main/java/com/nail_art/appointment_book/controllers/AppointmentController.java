package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.services.AppointmentService;
import jakarta.validation.Valid;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequestMapping("/appointments")
@RestController
public class AppointmentController {
    @Autowired
    AppointmentService appointmentService;

    @GetMapping("/")
    public ResponseEntity<List<Appointment>> getAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Optional<Appointment>> getAppointment(@PathVariable int id) {
        return ResponseEntity.ok(appointmentService.getAppointmentById(id));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Appointment>> getAppointmentsByDate(@PathVariable String date) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByDate(date));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody Appointment appointment, BindingResult result) {
        if (result.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
            return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
        }
        Appointment appt = appointmentService.createAppointment(appointment);

        return new ResponseEntity<>(appt, HttpStatus.CREATED);
    }

    @PutMapping("/edit")
    public ResponseEntity<Optional<Appointment>> editAppointment(@RequestBody Appointment appointment) {
        Optional<Appointment> appt = appointmentService.editAppointment(appointment);
        if (appt.isEmpty()) {
            return new ResponseEntity<>(Optional.of(appointment), HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(appointmentService.getAppointmentById(appointment.getId()), HttpStatus.OK);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Appointment> deleteAppointment(@RequestBody Appointment appointment) {
        boolean isDeleted = appointmentService.deleteAppointment(appointment);
        if (isDeleted) {
            return new ResponseEntity<>(appointment, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(appointment, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/search/{phoneNumber}")
    public ResponseEntity<List<Appointment>> getAppointments(@PathVariable String phoneNumber) {
       List<Appointment> appointments = appointmentService.getAppointmentsByPhoneNumber(phoneNumber);
       return ResponseEntity.ok(appointments);
    }
}
