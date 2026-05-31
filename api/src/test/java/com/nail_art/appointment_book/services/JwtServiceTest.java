package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.RefreshToken;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private JwtService jwtService;

    // Valid HS256 key (base64-encoded 256-bit key)
    private static final String TEST_SECRET = "dGVzdHNlY3JldGtleXRoYXRpc2xvbmdlbm91Z2hmb3JoczI1Ng==";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L); // 1 hour
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 86400000L); // 24 hours
    }

    private User makeUser(String username) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setPassword("encoded");
        return user;
    }

    @Nested
    class TokenGeneration {

        @Test
        void generatesValidToken() {
            User user = makeUser("admin");

            String token = jwtService.generateToken(user);

            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        void extractsUsernameFromToken() {
            User user = makeUser("admin");
            String token = jwtService.generateToken(user);

            String username = jwtService.extractUsername(token);

            assertEquals("admin", username);
        }

        @Test
        void tokenIsValidForCorrectUser() {
            User user = makeUser("admin");
            String token = jwtService.generateToken(user);

            assertTrue(jwtService.isTokenValid(token, user));
        }

        @Test
        void tokenIsInvalidForDifferentUser() {
            User user = makeUser("admin");
            User other = makeUser("other");
            String token = jwtService.generateToken(user);

            assertFalse(jwtService.isTokenValid(token, other));
        }

        @Test
        void tokenIsNotExpiredWhenFresh() {
            User user = makeUser("admin");
            String token = jwtService.generateToken(user);

            assertFalse(jwtService.isTokenExpired(token));
        }

        @Test
        void expiredTokenThrowsOnParse() {
            ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L); // already expired
            User user = makeUser("admin");
            String token = jwtService.generateToken(user);

            assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () ->
                    jwtService.extractUsername(token));
        }
    }

    @Nested
    class RefreshTokens {

        @Test
        void generatesRefreshTokenAndSaves() {
            User user = makeUser("admin");
            when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            UUID organizationId = UUID.randomUUID();
            String token = jwtService.generateRefreshToken(user, organizationId);

            assertNotNull(token);
            verify(refreshTokenRepository).save(argThat(refreshToken ->
                    refreshToken.getId() != null
                            && refreshToken.getUserId().equals(user.getId())
                            && refreshToken.getOrganizationId().equals(organizationId)
                            && refreshToken.getTokenHash().equals(jwtService.hashRefreshToken(token))
                            && !refreshToken.getTokenHash().equals(token)
            ));
        }

        @Test
        void validatesNonExpiredRefreshToken() {
            String token = "valid_token";
            RefreshToken rt = new RefreshToken();
            rt.setTokenHash(jwtService.hashRefreshToken(token));
            rt.setExpiryDate(Instant.now().plusSeconds(3600));

            when(refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(token))).thenReturn(Optional.of(rt));

            assertTrue(jwtService.validateRefreshToken(token));
        }

        @Test
        void rejectsExpiredRefreshToken() {
            String token = "expired_token";
            RefreshToken rt = new RefreshToken();
            rt.setTokenHash(jwtService.hashRefreshToken(token));
            rt.setExpiryDate(Instant.now().minusSeconds(3600));

            when(refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(token))).thenReturn(Optional.of(rt));

            assertFalse(jwtService.validateRefreshToken(token));
        }

        @Test
        void rejectsNonExistentRefreshToken() {
            String token = "fake_token";
            when(refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(token))).thenReturn(Optional.empty());

            assertFalse(jwtService.validateRefreshToken(token));
        }

        @Test
        void deletesRefreshToken() {
            String token = "to_delete";
            RefreshToken rt = new RefreshToken();
            rt.setTokenHash(jwtService.hashRefreshToken(token));

            when(refreshTokenRepository.findByTokenHash(jwtService.hashRefreshToken(token))).thenReturn(Optional.of(rt));

            jwtService.deleteRefreshToken(token);

            verify(refreshTokenRepository).delete(rt);
        }
    }
}
