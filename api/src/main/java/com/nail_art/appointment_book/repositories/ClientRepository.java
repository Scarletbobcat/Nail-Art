package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Client;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClientRepository extends MongoRepository<Client, String> {
    Optional<Client> findById(long id);
    Optional<Client> findByPhoneNumber(String phoneNumber);
}
