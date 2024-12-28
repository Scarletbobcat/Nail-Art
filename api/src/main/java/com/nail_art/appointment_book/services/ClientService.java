package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ClientService {
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CounterService counterService;

    public List<Client> getAllClients() {
        return clientRepository.findAll();
    }

    public Client getClientById(long id) {
        return clientRepository.findById(id).orElse(null);
    }

    public Client createClient(Client client) {
        long id = counterService.getNextSequence("Appointments");
        client.setId(id);
        return clientRepository.save(client);
    }

    public void deleteAppointmentFromClient(long clientId, long appointmentId) {
        Client tempClient = clientRepository.findById(clientId).orElse(null);
        if (tempClient != null) {
            List<Long> appointments = tempClient.getAppointmentIds();
            appointments.remove(appointmentId);
            tempClient.setAppointmentIds(appointments);
            clientRepository.save(tempClient);
        }
    }

    public void addAppointmentToClient(long clientId, long appointmentId) {
        Client tempClient = clientRepository.findById(clientId).orElse(null);
        if (tempClient != null) {
            List<Long> appointments = tempClient.getAppointmentIds();
            appointments.add(appointmentId);
            tempClient.setAppointmentIds(appointments);
            clientRepository.save(tempClient);
        }
    }

    public Optional<Client> editClient(Client client) {
        Client tempClient = this.getClientById(client.getId());
        if (tempClient != null) {
            tempClient.setName(client.getName());
            tempClient.setPhoneNumber(client.getPhoneNumber());
            tempClient.setAppointmentIds(client.getAppointmentIds());
            return Optional.of(clientRepository.save(tempClient));
        }
        return Optional.empty();
    }

    public Boolean deleteClient(Client client) {
        Client tempClient = getClientById(client.getId());
        if (tempClient != null) {
            clientRepository.delete(tempClient);
            return true;
        }
        return false;
    }
}
