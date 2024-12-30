package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Appointment;
import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ClientService {
    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CounterService counterService;

    @Autowired
    private MongoTemplate mongoTemplate;

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

    public List<Client> searchClients(Client client) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();
        for (Field field : client.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                if (field.get(client) != null) {
                    criteria.add(Criteria.where(field.getName()).is(field.get(client)));
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        if (!criteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }
        return mongoTemplate.find(query, Client.class);
    }
}
