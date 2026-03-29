package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Client;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import com.nail_art.appointment_book.services.ClientService;

import java.util.Optional;

@RestController
@RequestMapping("/clients")
public class ClientController {
    @Autowired
    ClientService clientService;

    @GetMapping("/{id}")
    public ResponseEntity<Client> getClient(@PathVariable int id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @PostMapping("/create")
    public ResponseEntity<Client> createClient(@Valid @RequestBody Client client, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(client);
        }
        return ResponseEntity.ok(clientService.createClient(client));
    }

    @PutMapping("/edit")
    public ResponseEntity<Client> editClient(@RequestBody Client client) {
        Optional<Client> editedClient = clientService.editClient(client);
        if (editedClient.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(editedClient.get());
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Boolean> deleteClient(@RequestBody Client client) {
        return ResponseEntity.ok(clientService.deleteClient(client));
    }

    @GetMapping("/")
    public ResponseEntity<Page<Client>> searchClients(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("id").descending());
        Client query = new Client();
        query.setName(name);
        query.setPhoneNumber(phoneNumber);
        if (query.getName() == null && query.getPhoneNumber() == null) {
            return ResponseEntity.ok(clientService.getAllClients(pageable));
        }
        return ResponseEntity.ok(clientService.searchClients(query, pageable));
    }
}
