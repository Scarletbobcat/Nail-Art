package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Counter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CounterRepository extends MongoRepository<Counter, String> {
    Optional<Counter> findByCollectionName(String collectionName);
}
