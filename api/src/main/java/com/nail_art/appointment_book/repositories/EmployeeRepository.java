package com.nail_art.appointment_book.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import com.nail_art.appointment_book.entities.Employee;

import java.util.Optional;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    Employee findByName(String name);
    Optional<Employee> findById(long id);
    Employee[] findAllByName(String name);
}
