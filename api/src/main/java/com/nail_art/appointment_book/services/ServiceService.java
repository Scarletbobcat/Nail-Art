package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.Service;
import com.nail_art.appointment_book.repositories.ServiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Service
public class ServiceService {
    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public Page<Service> getAllServices(Pageable pageable) {
        return serviceRepository.findAll(pageable);
    }

    public Page<Service> searchServices(String name, Pageable pageable) {
        return serviceRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public Service createService(Service service) {
        service.setUnavailabilityMarker(false);
        return serviceRepository.save(service);
    }

    public List<Service> getServiceByName(String name) {
        return serviceRepository.findByNameContainingIgnoreCase(name);
    }

    public Optional<Service> editService(UUID id, Service service) {
        return serviceRepository.findScopedById(id).map(existing -> {
            existing.setName(service.getName());
            return serviceRepository.save(existing);
        });
    }

    public boolean deleteService(UUID id) {
        return serviceRepository.findScopedById(id)
                .map(service -> {
                    serviceRepository.delete(service);
                    return true;
                })
                .orElse(false);
    }
}
