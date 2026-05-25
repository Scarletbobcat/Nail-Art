package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findScopedById(UUID id);

    Optional<Client> findByPhoneNumber(String phoneNumber);

    List<Client> findByNameContainingIgnoreCase(String name);

    Page<Client> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Client> findByPhoneNumberContainingIgnoreCase(String phoneNumber);

    Page<Client> findByPhoneNumberContainingIgnoreCase(String phoneNumber, Pageable pageable);

    Page<Client> findByNameContainingIgnoreCaseAndPhoneNumberContainingIgnoreCase(
            String name,
            String phoneNumber,
            Pageable pageable
    );
}
