package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByUserId(UUID userId);

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByUserId(UUID userId);

    default void deleteRefreshTokensByUsername(String username) {
        // Temporary bridge for the pre-cutover JwtService; removed when auth switches to user IDs.
    }
}
