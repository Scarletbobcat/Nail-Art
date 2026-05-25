package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findScopedById(UUID id);

    List<Employee> findByNameContainingIgnoreCase(String name);

    Page<Employee> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("select coalesce(max(e.displayOrder), -1) from Employee e")
    int findMaxDisplayOrder();
}
