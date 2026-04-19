package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {
    @Autowired
    AppointmentRepository appointmentRepository;
    @Autowired
    private CounterService counterService;
    @Autowired
    private ClientRepository clientRepository;

    // Helper to ensure time is formatted like "THH:mm" with zero-padded hour
    private String normalizeTime(String time) {
        if (time == null || time.isBlank()) {
            throw new IllegalArgumentException("Time must not be null or empty");
        }
        String t = time.startsWith("T") ? time.substring(1) : time;
        String[] parts = t.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid time format, expected H:mm or THH:mm");
        }
        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());
        return String.format("T%02d:%02d", hour, minute);
    }

    private void checkForConflicts(String date, long employeeId, String startTime, String endTime, long excludeId) {
        List<Appointment> existing = appointmentRepository.findByDateAndEmployeeId(date, employeeId);
        LocalDateTime newStart = LocalDateTime.parse(date + startTime);
        LocalDateTime newEnd = LocalDateTime.parse(date + endTime);
        for (Appointment appt : existing) {
            if (appt.getId() == excludeId) {
                continue;
            }
            LocalDateTime existStart = LocalDateTime.parse(appt.getDate() + appt.getStartTime());
            LocalDateTime existEnd = LocalDateTime.parse(appt.getDate() + appt.getEndTime());
            if (newStart.isBefore(existEnd) && newEnd.isAfter(existStart)) {
                throw new IllegalArgumentException("Time slot conflicts with an existing appointment");
            }
        }
    }

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    public Optional<Appointment> getAppointmentById(long id) {
        return appointmentRepository.findById(id);
    }

    public List<Appointment> getAppointmentsByDate(String date) {
        return appointmentRepository.findByDate(date);
    }

    public Appointment createAppointment(Appointment appointment) {
        // normalize times so parsing is consistent (expected format: date + THH:mm)
        appointment.setStartTime(normalizeTime(appointment.getStartTime()));
        appointment.setEndTime(normalizeTime(appointment.getEndTime()));

        LocalDateTime startDate = LocalDateTime.parse(appointment.getDate() + appointment.getStartTime());
        LocalDateTime endDate = LocalDateTime.parse(appointment.getDate() + appointment.getEndTime());
        if (startDate.compareTo(endDate) > -1)
            throw new IllegalArgumentException("End time must be after start time"); {
        }
        checkForConflicts(appointment.getDate(), appointment.getEmployeeId(),
                appointment.getStartTime(), appointment.getEndTime(), -1);
        long id = counterService.getNextSequence("Appointments");
        appointment.setId(id);
        appointment.setReminderSent(false);
        if (appointment.getClientId() == null && !appointment.getPhoneNumber().isEmpty()) {
            Client tempClient = clientRepository.findByPhoneNumber(appointment.getPhoneNumber()).orElse(null);
            if (tempClient == null) {
                Client client = new Client();
                client.setName(appointment.getName());
                client.setPhoneNumber(appointment.getPhoneNumber());
                long clientId = counterService.getNextSequence("Clients");
                client.setId(clientId);
                appointment.setClientId(clientId);
                clientRepository.save(client);
            } else {
                appointment.setClientId(tempClient.getId());
                appointment.setName(tempClient.getName());
            }
        }
        return appointmentRepository.save(appointment);
    }

    public Optional<Appointment> editAppointment(Appointment appointment) {
        // normalize times before validation and saving
        appointment.setStartTime(normalizeTime(appointment.getStartTime()));
        appointment.setEndTime(normalizeTime(appointment.getEndTime()));

        LocalDateTime startDate = LocalDateTime.parse(appointment.getDate() + appointment.getStartTime());
        LocalDateTime endDate = LocalDateTime.parse(appointment.getDate() + appointment.getEndTime());
        if (startDate.compareTo(endDate) > -1)
            throw new IllegalArgumentException("End time must be after start time"); {
        }
        checkForConflicts(appointment.getDate(), appointment.getEmployeeId(),
                appointment.getStartTime(), appointment.getEndTime(), appointment.getId());
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            if (!appointment.getStartTime().equals(tempAppointment.get().getStartTime()) || !appointment.getDate().equals(tempAppointment.get().getDate())) {
                appointment.setReminderSent(false);
            }
            tempAppointment.get().setReminderSent(appointment.getReminderSent());
            tempAppointment.get().setServices(appointment.getServices());
            tempAppointment.get().setDate(appointment.getDate());
            tempAppointment.get().setName(appointment.getName());
            tempAppointment.get().setEmployeeId(appointment.getEmployeeId());
            tempAppointment.get().setStartTime(appointment.getStartTime());
            tempAppointment.get().setEndTime(appointment.getEndTime());
            tempAppointment.get().setPhoneNumber(appointment.getPhoneNumber());
            tempAppointment.get().setShowedUp(appointment.getShowedUp());

            // link client if appointment has a phone number but no clientId
            Long clientId = appointment.getClientId();
            if (clientId == null && appointment.getPhoneNumber() != null && !appointment.getPhoneNumber().isEmpty()) {
                Client matchedClient = clientRepository.findByPhoneNumber(appointment.getPhoneNumber()).orElse(null);
                if (matchedClient != null) {
                    clientId = matchedClient.getId();
                    tempAppointment.get().setClientId(clientId);
                }
            }
            if (clientId == null) {
                return Optional.of(appointmentRepository.save(tempAppointment.get()));
            }
            Client client = clientRepository.findById(clientId).orElse(null);
            if (client != null) {
                client.setName(appointment.getName());
                client.setPhoneNumber(appointment.getPhoneNumber());
                // updating all appointments with the same client id
                List<Appointment> tempAppointments = appointmentRepository.findByClientId(client.getId());
                for (Appointment clientAppointment : tempAppointments) {
                    if (clientAppointment.getId() == appointment.getId()) {
                        continue;
                    }
                    clientAppointment.setName(client.getName());
                    clientAppointment.setPhoneNumber(client.getPhoneNumber());
                    basicEdit(clientAppointment);
                }
                clientRepository.save(client);
            }
            return Optional.of(appointmentRepository.save(tempAppointment.get()));
        }
        return Optional.empty();
    }

    public void markReminderSent(long appointmentId) {
        Optional<Appointment> appointment = getAppointmentById(appointmentId);
        if (appointment.isPresent()) {
            appointment.get().setReminderSent(true);
            appointmentRepository.save(appointment.get());
        }
    }

    private void basicEdit(Appointment appointment) {
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            tempAppointment.get().setServices(appointment.getServices());
            tempAppointment.get().setDate(appointment.getDate());
            tempAppointment.get().setName(appointment.getName());
            tempAppointment.get().setEmployeeId(appointment.getEmployeeId());
            // ensure times are normalized when applying edits
            tempAppointment.get().setStartTime(normalizeTime(appointment.getStartTime()));
            tempAppointment.get().setEndTime(normalizeTime(appointment.getEndTime()));
            tempAppointment.get().setPhoneNumber(appointment.getPhoneNumber());
            tempAppointment.get().setReminderSent(appointment.getReminderSent());
            tempAppointment.get().setShowedUp(appointment.getShowedUp());
            appointmentRepository.save(tempAppointment.get());
        }
    }

    public Boolean deleteAppointment(Appointment appointment) {
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            appointmentRepository.delete(tempAppointment.get());
            return true;
        }
        return false;
    }

    public List<Appointment> getAppointmentsByPhoneNumber(String phoneNumber) {
        return appointmentRepository.findByPhoneNumberContaining(phoneNumber);
    }

    public List<Appointment> getAppointmentsForTomorrow() {
        String date = LocalDate.now(ZoneId.of("America/New_York")).plusDays(1).toString();
        return appointmentRepository.findByDate(date);
    }
}
