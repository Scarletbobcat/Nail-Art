package com.nail_art.appointment_book.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport.SeededIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthenticationControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    private PostgresIdentityTestSupport identitySupport;

    @BeforeEach
    void setUpIdentity() {
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        identitySupport.resetIdentityTables();
    }

    @Test
    void login_validCreds_returnsJwtWithUuidSubAndOrgAndRole() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("NailArt", "owner");

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("NailArt", PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode claims = identitySupport.decodeJwtPayload(objectMapper.readTree(body).get("token").asText());

        assertEquals(identity.userId().toString(), UUID.fromString(claims.get("sub").asText()).toString());
        assertEquals(identity.organizationId().toString(), UUID.fromString(claims.get("org").asText()).toString());
        assertEquals("owner", claims.get("role").asText());
    }

    @Test
    void login_validCreds_setsRefreshCookieWithCorrectAttributes() throws Exception {
        identitySupport.seedIdentity("NailArt", "owner");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("NailArt", PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=None")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=2592000")));
    }

    @Test
    void login_caseDifferentUsername_succeeds() throws Exception {
        identitySupport.seedIdentity("NailArt", "owner");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nailart", PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        identitySupport.seedIdentity("NailArt", "owner");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("NailArt", "wrong-pass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_validCookie_returnsNewToken() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("NailArt", "owner");
        String refreshToken = identitySupport.persistRefreshToken(identity.userId(), identity.organizationId(), "owner");

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void refresh_revokedCookie_returns401() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("NailArt", "owner");
        String refreshToken = identitySupport.signedToken(identity.userId(), identity.organizationId(), "owner");

        mockMvc.perform(post("/auth/refresh").cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_endpointDeleted_returns404() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "new-owner",
                                  "password": "secret-pass",
                                  "email": "owner@example.com"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void refresh_afterRoleChange_returnsTokenWithNewRole() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("NailArt", "staff");
        String refreshToken = identitySupport.persistRefreshToken(identity.userId(), identity.organizationId(), "staff");

        jdbcTemplate.update(
                "update organization_users set role = 'owner' where organization_id = ? and user_id = ?",
                identity.organizationId(),
                identity.userId()
        );

        String body = mockMvc.perform(post("/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode claims = identitySupport.decodeJwtPayload(objectMapper.readTree(body).get("token").asText());

        assertEquals("owner", claims.get("role").asText());
    }

    private String loginBody(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
