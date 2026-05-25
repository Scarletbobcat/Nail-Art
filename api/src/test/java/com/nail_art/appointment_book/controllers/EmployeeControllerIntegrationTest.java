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

class EmployeeControllerIntegrationTest extends PostgresIntegrationTest {
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
        jdbcTemplate.update("delete from employees");
        identitySupport.resetIdentityTables();
        ownerA = identitySupport.seedIdentity("owner-a", "owner");
        ownerB = identitySupport.seedIdentity("owner-b", "owner");
    }

    @Test
    void postCreate_validBody_returns201() throws Exception {
        mockMvc.perform(post("/employees/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "color": "#ff0000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getByName_returnsOrgScopedResults() throws Exception {
        UUID orgAEmployeeId = insertEmployee(ownerA.organizationId(), "Anna", "#111111");
        UUID orgBEmployeeId = insertEmployee(ownerB.organizationId(), "Joanne", "#222222");

        mockMvc.perform(get("/employees/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("name", "ann"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orgAEmployeeId.toString()));

        assertThat(orgBEmployeeId)
                .as("operation=controller search activeOrg=%s seedOrgA=%s seedOrgB=%s orgAEmployee=%s orgBEmployee=%s",
                        ownerA.organizationId(), ownerA.organizationId(), ownerB.organizationId(), orgAEmployeeId, orgBEmployeeId)
                .isNotEqualTo(orgAEmployeeId);
    }

    @Test
    void postCreate_emptyName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/employees/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "color": "#ff0000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isString());
    }

    @Test
    void deleteByIdOfAnotherOrg_returns404() throws Exception {
        UUID targetEmployeeId = insertEmployee(ownerB.organizationId(), "Bea", "#222222");

        mockMvc.perform(delete("/employees/delete/{id}", targetEmployeeId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isNotFound());

        assertThat(employeeExists(targetEmployeeId))
                .as("operation=cross-org delete attackerOrg=%s targetStoredOrg=%s targetEmployee=%s expectedStatus=404",
                        ownerA.organizationId(), ownerB.organizationId(), targetEmployeeId)
                .isTrue();
    }

    @Test
    void putEditByIdOfAnotherOrg_returns404() throws Exception {
        UUID targetEmployeeId = insertEmployee(ownerB.organizationId(), "Bea", "#222222");

        mockMvc.perform(put("/employees/edit/{id}", targetEmployeeId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mallory",
                                  "color": "#000000"
                                }
                                """))
                .andExpect(status().isNotFound());

        assertThat(employeeName(targetEmployeeId))
                .as("operation=cross-org edit attackerOrg=%s targetStoredOrg=%s targetEmployee=%s expectedStatus=404",
                        ownerA.organizationId(), ownerB.organizationId(), targetEmployeeId)
                .isEqualTo("Bea");
    }

    @Test
    void endToEnd_createListEditDelete_returnsExpectedStatuses() throws Exception {
        String createResponse = mockMvc.perform(post("/employees/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "color": "#ff0000"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID employeeId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(get("/employees/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(employeeId.toString()));

        mockMvc.perform(put("/employees/edit/{id}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice Smith",
                                  "color": "#00ff00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"));

        mockMvc.perform(delete("/employees/delete/{id}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk());

        assertThat(employeeExists(employeeId))
                .as("operation=end-to-end delete activeOrg=%s employeeId=%s", ownerA.organizationId(), employeeId)
                .isFalse();
    }

    private UUID insertEmployee(UUID organizationId, String name, String color) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                color
        );
    }

    private boolean employeeExists(UUID employeeId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from employees where id = ?",
                Integer.class,
                employeeId
        );
        return count != null && count == 1;
    }

    private String employeeName(UUID employeeId) {
        return jdbcTemplate.queryForObject(
                "select name from employees where id = ?",
                String.class,
                employeeId
        );
    }
}
