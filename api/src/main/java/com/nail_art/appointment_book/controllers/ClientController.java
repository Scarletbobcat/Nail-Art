package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Client;
import com.nail_art.appointment_book.services.ClientService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientController {
    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClient(@PathVariable UUID id) {
        Optional<Client> client = clientService.getClientById(id);
        if (client.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(client.get());
    }

    @PostMapping("/create")
    public ResponseEntity<?> createClient(@Valid @RequestBody Client client, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        return new ResponseEntity<>(clientService.createClient(client), HttpStatus.CREATED);
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<Client> editClient(@PathVariable UUID id, @Valid @RequestBody Client client) {
        Optional<Client> editedClient = clientService.editClient(id, client);
        if (editedClient.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(editedClient.get());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        if (!clientService.deleteClient(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/")
    public ResponseEntity<Page<Client>> searchClients(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 2000), Sort.by("id").descending());
        Client query = new Client();
        query.setName(name);
        query.setPhoneNumber(phoneNumber);
        if (query.getName() == null && query.getPhoneNumber() == null) {
            return ResponseEntity.ok(clientService.getAllClients(pageable));
        }
        return ResponseEntity.ok(clientService.searchClients(query, pageable));
    }
}
