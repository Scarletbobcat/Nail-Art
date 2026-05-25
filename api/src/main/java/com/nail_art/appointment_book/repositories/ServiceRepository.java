package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {
    @Query("select s from Service s where s.id = :id")
    Optional<Service> findScopedById(UUID id);

    List<Service> findByNameContainingIgnoreCase(String name);

    Page<Service> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
