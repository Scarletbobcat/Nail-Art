package com.nail_art.appointment_book.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.multitenancy.TenantContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClientControllerIntegrationTest extends PostgresIntegrationTest {
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
    private SeededIdentity ownerA;
    private SeededIdentity ownerB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from clients");
        identitySupport.resetIdentityTables();
        ownerA = identitySupport.seedIdentity("owner-a", "owner");
        ownerB = identitySupport.seedIdentity("owner-b", "owner");
    }

    @Test
    void postCreate_validBody_returns201() throws Exception {
        mockMvc.perform(post("/clients/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anna",
                                  "phoneNumber": "330-555-1234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Anna"));
    }

    @Test
    void getByName_returnsOrgScopedResults() throws Exception {
        UUID orgAClientId = insertClient(ownerA.organizationId(), "Anna", "330-555-1000");
        UUID orgBClientId = insertClient(ownerB.organizationId(), "Joanne", "330-555-1001");

        mockMvc.perform(get("/clients/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("name", "ann"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orgAClientId.toString()));

        assertThat(orgBClientId).isNotEqualTo(orgAClientId);
    }

    @Test
    void paginationSizeUnderCap_returnsRequested() throws Exception {
        mockMvc.perform(get("/clients/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(500));
    }

    @Test
    void paginationSizeAtCap_returnsCap() throws Exception {
        mockMvc.perform(get("/clients/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("size", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2000));
    }

    @Test
    void paginationSizeOverCap_cappedAt2000() throws Exception {
        mockMvc.perform(get("/clients/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("size", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(2000));
    }

    @Test
    void duplicatePhone_returns409WithDescriptionBody() throws Exception {
        insertClient(ownerA.organizationId(), "Anna", "330-555-1234");

        mockMvc.perform(post("/clients/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bea",
                                  "phoneNumber": "330-555-1234"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.description").isString());
    }

    @Test
    void putEditByIdOfAnotherOrg_returns404() throws Exception {
        UUID targetClientId = insertClient(ownerB.organizationId(), "Bea", "330-555-1001");

        mockMvc.perform(put("/clients/edit/{id}", targetClientId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mallory",
                                  "phoneNumber": "330-555-9999"
                                }
                                """))
                .andExpect(status().isNotFound());

        assertThat(clientName(targetClientId)).isEqualTo("Bea");
    }

    @Test
    void deleteByIdOfAnotherOrg_returns404() throws Exception {
        UUID targetClientId = insertClient(ownerB.organizationId(), "Bea", "330-555-1001");

        mockMvc.perform(delete("/clients/delete/{id}", targetClientId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isNotFound());

        assertThat(clientExists(targetClientId)).isTrue();
    }

    private UUID insertClient(UUID organizationId, String name, String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "insert into clients (organization_id, name, phone_number) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                phoneNumber
        );
    }

    private boolean clientExists(UUID clientId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from clients where id = ?",
                Integer.class,
                clientId
        );
        return count != null && count == 1;
    }

    private String clientName(UUID clientId) {
        return jdbcTemplate.queryForObject(
                "select name from clients where id = ?",
                String.class,
                clientId
        );
    }
}
