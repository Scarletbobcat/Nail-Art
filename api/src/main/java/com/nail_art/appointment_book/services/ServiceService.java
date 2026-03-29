package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.repositories.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ServiceService {
    @Autowired
    private ServiceRepository serviceRepository;
    @Autowired
    private CounterService counterService;

    public Page<com.nail_art.appointment_book.entities.Service> getAllServices(Pageable pageable) {
        return serviceRepository.findAll(pageable);
    }

    public Page<com.nail_art.appointment_book.entities.Service> searchServices(String name, Pageable pageable) {
        return serviceRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    public com.nail_art.appointment_book.entities.Service createService(com.nail_art.appointment_book.entities.Service service) {
        service.setId(counterService.getNextSequence("Services"));
        return serviceRepository.save(service);
    }
    public com.nail_art.appointment_book.entities.Service[] getServiceByName(String name) {
        return serviceRepository.findAllByName(name);
    }

    public com.nail_art.appointment_book.entities.Service editService(com.nail_art.appointment_book.entities.Service service) {
        com.nail_art.appointment_book.entities.Service tempService = serviceRepository.findById(service.getId()).orElse(null);
        if (tempService == null) {
            return null;
        }
        tempService.setName(service.getName());
        return serviceRepository.save(tempService);
    }

    public com.nail_art.appointment_book.entities.Service deleteService(com.nail_art.appointment_book.entities.Service service) {
        com.nail_art.appointment_book.entities.Service tempService = serviceRepository.findById(service.getId()).orElse(null);
        if (tempService == null) {
            return null;
        }
        serviceRepository.delete(tempService);
        return tempService;
    }
}
