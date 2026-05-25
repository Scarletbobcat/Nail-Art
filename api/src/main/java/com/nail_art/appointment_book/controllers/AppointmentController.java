package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.services.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequestMapping("/appointments")
@RestController
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/")
    public ResponseEntity<List<Appointment>> getAppointments() {
        return ResponseEntity.ok(appointmentService.getAllAppointments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getAppointment(@PathVariable UUID id) {
        return appointmentService.getAppointmentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<Appointment>> getAppointmentsByDate(@PathVariable LocalDate date) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByDate(date));
    }

    @PostMapping("/create")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody Appointment appointment, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        try {
            return new ResponseEntity<>(appointmentService.createAppointment(appointment), HttpStatus.CREATED);
        } catch (IllegalArgumentException exception) {
            return conflict(exception);
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.badRequest().body(Map.of("description", exception.getMessage()));
        }
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody Appointment appointment,
            BindingResult result
    ) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        try {
            return appointmentService.editAppointment(id, appointment)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException exception) {
            return conflict(exception);
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.badRequest().body(Map.of("description", exception.getMessage()));
        }
    }

    @PutMapping("/edit")
    public ResponseEntity<?> editAppointment(@Valid @RequestBody Appointment appointment, BindingResult result) {
        return editAppointment(appointment.getId(), appointment, result);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteAppointment(@PathVariable UUID id) {
        if (appointmentService.deleteAppointment(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteAppointment(@RequestBody Appointment appointment) {
        return deleteAppointment(appointment.getId());
    }

    @GetMapping("/search/{phoneNumber}")
    public ResponseEntity<List<Appointment>> getAppointments(@PathVariable String phoneNumber) {
        return ResponseEntity.ok(appointmentService.getAppointmentsByPhoneNumber(phoneNumber));
    }

    private ResponseEntity<Map<String, String>> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("description", exception.getMessage()));
    }
}
