package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.services.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/services")
@RestController
public class ServiceController {
    @Autowired
    private ServiceService serviceService;

    @GetMapping("/")
    public ResponseEntity<List<Service>> getServices() {
        return ResponseEntity.ok(serviceService.getAllServices());
    }

    @PostMapping("/create")
    public ResponseEntity<Service> createService(@RequestBody Service service) {
        return ResponseEntity.ok(serviceService.createService(service));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<Service[]> getServiceByName(@PathVariable String name) {
        name = name.replace("%20", " ");
        return ResponseEntity.ok(serviceService.getServiceByName(name));
    }

    @PutMapping("/edit")
    public ResponseEntity<Service> editService(@RequestBody Service service) {
        return ResponseEntity.ok(serviceService.editService(service));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Service> deleteService(@RequestBody Service service) {
        return ResponseEntity.ok(serviceService.deleteService(service));
    }
}
