package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;

@Service
public class AppointmentService {
    @Autowired
    AppointmentRepository appointmentRepository;
    @Autowired
    private CounterService counterService;
    @Autowired
    private ClientService clientService;

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
        long id = counterService.getNextSequence("Appointments");
        appointment.setId(id);
        appointment.setReminderSent(false);
        appointment.setClientId(0L);
        if (appointment.getClientId() == null) {
            Client client = new Client();
            client.setName(appointment.getName());
            client.setPhoneNumber(appointment.getPhoneNumber());
            client.setAppointmentIds(List.of(id));
            clientService.createClient(client);
        } else {
            clientService.addAppointmentToClient(appointment.getClientId(), id);
        }
        return appointmentRepository.save(appointment);
    }

    public Optional<Appointment> editAppointment(Appointment appointment) {
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            tempAppointment.get().setServices(appointment.getServices());
            tempAppointment.get().setDate(appointment.getDate());
            tempAppointment.get().setName(appointment.getName());
            tempAppointment.get().setEmployeeId(appointment.getEmployeeId());
            tempAppointment.get().setStartTime(appointment.getStartTime());
            tempAppointment.get().setEndTime(appointment.getEndTime());
            tempAppointment.get().setPhoneNumber(appointment.getPhoneNumber());
            tempAppointment.get().setReminderSent(appointment.getReminderSent());
            tempAppointment.get().setShowedUp(appointment.getShowedUp());
            return Optional.of(appointmentRepository.save(tempAppointment.get()));
        }
        return Optional.empty();
    }

    public Boolean deleteAppointment(Appointment appointment) {
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            appointmentRepository.delete(tempAppointment.get());
            if (appointment.getClientId() != null) {
                clientService.deleteAppointmentFromClient(appointment.getClientId(), appointment.getId());
            }
            return true;
        }
        return false;
    }

    public List<Appointment> getAppointmentsByPhoneNumber(String phoneNumber) {
        return appointmentRepository.findByPhoneNumber(phoneNumber);
    }

    public List<Appointment> getAppointmentsNextWorkDay() {
        Calendar calendar = Calendar.getInstance();
        String date = "";
        List<Appointment> appointments = null;
        int daysChecked = 0;
        do {
            calendar.add(Calendar.DATE, 1);
            date = String.format("%d-%02d-%02d",
                    calendar.get(Calendar.YEAR)
                    ,(calendar.get(Calendar.MONTH) + 1)
                    ,calendar.get(Calendar.DATE));
            appointments = appointmentRepository.findByDate(date);
            daysChecked++;
        } while (appointments.isEmpty() && daysChecked < 30);
        return appointmentRepository.findByDate(date);
    }
}
