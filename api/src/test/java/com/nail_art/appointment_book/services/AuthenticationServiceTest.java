package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.dtos.LoginUserDto;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationServiceTest extends PostgresIntegrationTest {
    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    private PostgresIdentityTestSupport identitySupport;

    @BeforeEach
    void setUpIdentity() {
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, null, secretKey);
        identitySupport.resetIdentityTables();
    }

    @Test
    void authenticate_validCredentials_readsPostgresIdentity() {
        var identity = identitySupport.seedIdentity("NailArt", "owner");

        User result = authenticationService.authenticate(loginDto("NailArt", PostgresIdentityTestSupport.TEST_PASSWORD));

        assertEquals("NailArt", result.getUsername());
        assertNull(result.getPassword(), "auth service must not expose password hashes to controller responses");
        assertEquals(identity.organizationId().toString(), identitySupport.currentOrganizationIdFor("NailArt").toString());
    }

    @Test
    void authenticate_caseDifferentUsername_succeedsThroughCitext() {
        identitySupport.seedIdentity("NailArt", "owner");

        User result = authenticationService.authenticate(loginDto("nailart", PostgresIdentityTestSupport.TEST_PASSWORD));

        assertEquals("NailArt", result.getUsername());
    }

    @Test
    void authenticate_wrongPassword_throwsBadCredentials() {
        identitySupport.seedIdentity("NailArt", "owner");

        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(loginDto("NailArt", "wrong-pass"))
        );
    }

    @Test
    void generateRefreshToken_persistsHashedTokenForPostgresUserId() {
        identitySupport.seedIdentity("NailArt", "owner");
        User user = authenticationService.authenticate(loginDto("NailArt", PostgresIdentityTestSupport.TEST_PASSWORD));

        String token = authenticationService.generateRefreshToken(user);
        String tokenHash = identitySupport.hashRefreshToken(token);

        Integer savedTokens = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from refresh_tokens rt
                        join users u on u.id = rt.user_id
                        where u.username = ? and rt.token_hash = ?
                        """,
                Integer.class,
                "NailArt",
                tokenHash
        );
        assertEquals(1, savedTokens);

        Integer rawTokens = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?",
                Integer.class,
                token
        );
        assertEquals(0, rawTokens);
    }

    @Test
    void generateRefreshToken_keepsExistingDeviceSessions() {
        identitySupport.seedIdentity("NailArt", "owner");
        User user = authenticationService.authenticate(loginDto("NailArt", PostgresIdentityTestSupport.TEST_PASSWORD));

        authenticationService.generateRefreshToken(user);
        authenticationService.generateRefreshToken(user);

        Integer savedTokens = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from refresh_tokens rt
                        join users u on u.id = rt.user_id
                        where u.username = ?
                        """,
                Integer.class,
                "NailArt"
        );
        assertEquals(2, savedTokens);
    }

    private LoginUserDto loginDto(String username, String password) {
        LoginUserDto dto = new LoginUserDto();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }
}
