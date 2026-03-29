package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.services.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/services")
@RestController
public class ServiceController {
    @Autowired
    private ServiceService serviceService;

    @GetMapping("/")
    public ResponseEntity<Page<Service>> getServices(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("id").ascending());
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(serviceService.searchServices(name, pageable));
        }
        return ResponseEntity.ok(serviceService.getAllServices(pageable));
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
