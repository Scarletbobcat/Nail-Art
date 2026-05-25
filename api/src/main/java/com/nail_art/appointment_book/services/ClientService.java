package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.repositories.ClientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Page<Client> getAllClients(Pageable pageable) {
        return clientRepository.findAll(pageable);
    }

    public Optional<Client> getClientById(UUID id) {
        return clientRepository.findScopedById(id);
    }

    public Client createClient(Client client) {
        return clientRepository.save(client);
    }

    public Optional<Client> editClient(UUID id, Client client) {
        return clientRepository.findScopedById(id).map(existing -> {
            existing.setName(client.getName());
            existing.setPhoneNumber(client.getPhoneNumber());
            return clientRepository.save(existing);
        });
    }

    public Boolean deleteClient(UUID id) {
        return clientRepository.findScopedById(id)
                .map(client -> {
                    clientRepository.delete(client);
                    return true;
                })
                .orElse(false);
    }

    public Page<Client> searchClients(Client client, Pageable pageable) {
        boolean hasName = client.getName() != null && !client.getName().isBlank();
        boolean hasPhone = client.getPhoneNumber() != null && !client.getPhoneNumber().isBlank();
        if (hasName && hasPhone) {
            return clientRepository.findByNameContainingIgnoreCaseAndPhoneNumberContainingIgnoreCase(
                    client.getName(),
                    client.getPhoneNumber(),
                    pageable
            );
        }
        if (hasName) {
            return clientRepository.findByNameContainingIgnoreCase(client.getName(), pageable);
        }
        if (hasPhone) {
            return clientRepository.findByPhoneNumberContainingIgnoreCase(client.getPhoneNumber(), pageable);
        }
        return getAllClients(pageable);
    }
}
