package com.nail_art.appointment_book.controllers;

import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.services.ProductAnalytics;
import com.nail_art.appointment_book.services.ServiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequestMapping("/services")
@RestController
public class ServiceController {
    private final ServiceService serviceService;
    private final ProductAnalytics productAnalytics;

    public ServiceController(ServiceService serviceService, ProductAnalytics productAnalytics) {
        this.serviceService = serviceService;
        this.productAnalytics = productAnalytics;
    }

    @GetMapping("/")
    public ResponseEntity<Page<Service>> getServices(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name").ascending());
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(serviceService.searchServices(name, pageable));
        }
        return ResponseEntity.ok(serviceService.getAllServices(pageable));
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<?> createService(@Valid @RequestBody Service service, BindingResult result) {
        if (result.hasErrors()) {
            return ResponseEntity.badRequest().body(ControllerValidation.fieldErrors(result));
        }
        Service created = serviceService.createService(service);
        productAnalytics.capture("service_created", Map.of(
                "service_id", created.getId().toString(),
                "service_name", String.valueOf(created.getName())));
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<List<Service>> getServiceByName(@PathVariable String name) {
        return ResponseEntity.ok(serviceService.getServiceByName(name));
    }

    @PutMapping("/edit/{id}")
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<Service> editService(@PathVariable UUID id, @Valid @RequestBody Service service) {
        Optional<Service> editedService = serviceService.editService(id, service);
        if (editedService.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        productAnalytics.capture("service_edited", Map.of(
                "service_id", editedService.get().getId().toString(),
                "service_name", String.valueOf(editedService.get().getName())));
        return ResponseEntity.ok(editedService.get());
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('owner')")
    public ResponseEntity<Void> deleteService(@PathVariable UUID id) {
        // Resolve the name before the row is gone so the event stays readable.
        String serviceName = serviceService.getServiceById(id).map(Service::getName).orElse(null);
        if (!serviceService.deleteService(id)) {
            return ResponseEntity.notFound().build();
        }
        productAnalytics.capture("service_deleted", Map.of(
                "service_id", id.toString(),
                "service_name", String.valueOf(serviceName)));
        return ResponseEntity.ok().build();
    }
}
