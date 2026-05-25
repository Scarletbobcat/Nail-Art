package com.nail_art.appointment_book.controllers;

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

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerIntegrationTest extends PostgresIntegrationTest {
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
    void getMe_authenticated_returnsUserAndOrganization() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(identity.userId().toString()))
                .andExpect(jsonPath("$.user.username").value("owner"))
                .andExpect(jsonPath("$.user.role").value("owner"))
                .andExpect(jsonPath("$.organization.id").value(identity.organizationId().toString()))
                .andExpect(jsonPath("$.organization.name").value(PostgresIdentityTestSupport.TEST_ORGANIZATION_NAME))
                .andExpect(jsonPath("$.organization.timezone").value(PostgresIdentityTestSupport.TEST_TIMEZONE))
                .andExpect(jsonPath("$.organization.businessPhone").value(PostgresIdentityTestSupport.TEST_BUSINESS_PHONE));
    }

    @Test
    void getMe_responseBodyDoesNotContainPasswordHash() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        String body = mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .toLowerCase(Locale.ROOT);

        assertFalse(body.contains("passwordhash"));
        assertFalse(body.contains("password_hash"));
        assertFalse(body.contains("\"password\""));
    }

    @Test
    void getMe_noToken_returns401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_filterCrossCheckFails_returns401() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");
        jdbcTemplate.update(
                "delete from organization_users where organization_id = ? and user_id = ?",
                identity.organizationId(),
                identity.userId()
        );

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_filterCrossCheckThrows_returns503() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        jdbcTemplate.execute("alter table organization_users rename to organization_users_unavailable");
        try {
            mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            jdbcTemplate.execute("alter table organization_users_unavailable rename to organization_users");
        }
    }

    @Test
    void listUsers_endpointDeleted_returns404() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(get("/users/").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isNotFound());
    }

    @Test
    void postUsers_ownerToken_createsUserInCallersOrg() throws Exception {
        SeededIdentity owner = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateBody("staffer", "staffer@example.com", "staff")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("staffer"))
                .andExpect(jsonPath("$.user.role").value("staff"));

        assertEquals(1, identitySupport.countMemberships("staffer", owner.organizationId()));
    }

    @Test
    void postUsers_staffToken_returns403() throws Exception {
        SeededIdentity staff = identitySupport.seedIdentity("staffer", "staff");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateBody("new-user", "new-user@example.com", "staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    void postUsers_duplicateUsername_returns409() throws Exception {
        SeededIdentity owner = identitySupport.seedIdentity("owner", "owner");
        identitySupport.seedUserInOrganization(owner.organizationId(), "staffer", "staff");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateBody("STAFFER", "other@example.com", "staff")))
                .andExpect(status().isConflict());
    }

    @Test
    void postUsers_unrecognizedRole_returns400() throws Exception {
        SeededIdentity owner = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateBody("manager", "manager@example.com", "manager")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postUsers_explicitOrgIdInBody_ignored() throws Exception {
        SeededIdentity owner = identitySupport.seedIdentity("owner", "owner");
        UUID attackerRequestedOrg = identitySupport.createOrganization("Different Spa");

        mockMvc.perform(post("/users")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "staffer",
                                  "email": "staffer@example.com",
                                  "password": "secret-pass",
                                  "role": "staff",
                                  "organizationId": "%s"
                                }
                                """.formatted(attackerRequestedOrg)))
                .andExpect(status().isCreated());

        assertEquals(1, identitySupport.countMemberships("staffer", owner.organizationId()));
        assertEquals(0, identitySupport.countMemberships("staffer", attackerRequestedOrg));
    }

    private String userCreateBody(String username, String email, String role) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "password": "secret-pass",
                  "role": "%s"
                }
                """.formatted(username, email, role);
    }
}
