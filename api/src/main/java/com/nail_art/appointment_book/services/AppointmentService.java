package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.AppointmentRepository;
import com.nail_art.appointment_book.repositories.ClientRepository;
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
    private ClientRepository clientRepository;

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
        if (appointment.getClientId() == null && !appointment.getPhoneNumber().isEmpty()) {
            Client tempClient = clientRepository.findByPhoneNumber(appointment.getPhoneNumber()).orElse(null);
            if (tempClient == null) {
                Client client = new Client();
                client.setName(appointment.getName());
                client.setPhoneNumber(appointment.getPhoneNumber());
                client.setAppointmentIds(List.of(id));
                long clientId = counterService.getNextSequence("Appointments");
                client.setId(clientId);
                appointment.setClientId(clientId);
                clientRepository.save(client);
            } else {
                List<Long> appointments = tempClient.getAppointmentIds();
                appointments.add(appointment.getId());
                tempClient.setAppointmentIds(appointments);
                appointment.setClientId(tempClient.getId());
                appointment.setName(tempClient.getName());
                clientRepository.save(tempClient);
            }
        } else if (appointment.getClientId() != null) {
            Client tempClient = clientRepository.findById(appointment.getClientId()).orElse(null);
            if (tempClient != null) {
                List<Long> appointments = tempClient.getAppointmentIds();
                appointments.add(appointment.getId());
                tempClient.setAppointmentIds(appointments);
                clientRepository.save(tempClient);
            }
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

            // update client when editing appointment if client exists
            Long clientId = appointment.getClientId();
            if (clientId == null) {
                return Optional.of(appointmentRepository.save(tempAppointment.get()));
            }
            Client client = clientRepository.findById(appointment.getClientId()).orElse(null);
            System.out.println("Client: " + client);
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

    private void basicEdit(Appointment appointment) {
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
            appointmentRepository.save(tempAppointment.get());
        }
    }

    public Boolean deleteAppointment(Appointment appointment) {
        Optional<Appointment> tempAppointment = getAppointmentById(appointment.getId());
        if (tempAppointment.isPresent()) {
            appointmentRepository.delete(tempAppointment.get());
            Long clientId = appointment.getClientId();
            if (clientId != null) {
                Client tempClient = clientRepository.findById(clientId).orElse(null);
                if (tempClient != null) {
                    List<Long> appointments = tempClient.getAppointmentIds();
                    appointments.remove(appointment.getId());
                    tempClient.setAppointmentIds(appointments);
                    clientRepository.save(tempClient);
                }
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
