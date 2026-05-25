package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    default Optional<User> findByUsernameIgnoreCase(String username) {
        return findByUsername(username);
    }

    Optional<User> findByEmail(String email);
}
