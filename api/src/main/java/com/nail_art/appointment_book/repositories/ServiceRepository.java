package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Service;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceRepository extends MongoRepository<Service, String> {
    Service[] findAllByName(String name);
    Optional<Service> findById(long id);
}
