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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two-tier role enforcement (owner / staff). Staff do day-to-day appointment and client work;
 * only owners may mutate the employee roster, the service menu, or add users. Reads stay open to
 * staff because booking requires seeing employees and services.
 */
class RoleAuthorizationIntegrationTest extends PostgresIntegrationTest {
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
    private SeededIdentity owner;
    private SeededIdentity staff;
    private String ownerToken;
    private String staffToken;
    private UUID employeeId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from clients");
        jdbcTemplate.update("delete from services");
        jdbcTemplate.update("delete from employees");
        identitySupport.resetIdentityTables();

        owner = identitySupport.seedIdentity("owner", "owner");
        // staff belongs to the SAME org as owner, so any rejection is role-based, not tenant-based.
        staff = new SeededIdentity(
                owner.organizationId(),
                identitySupport.seedUserInOrganization(owner.organizationId(), "staff", "staff"),
                "staff",
                "staff"
        );
        ownerToken = identitySupport.bearer(owner);
        staffToken = identitySupport.bearer(staff);

        employeeId = insertEmployee(owner.organizationId(), "Anna", "#111111");
        serviceId = insertService(owner.organizationId(), "Gel Manicure");
    }

    @Test
    void staff_isForbiddenFromOwnerLevelMutations() throws Exception {
        // Roster mutations
        mockMvc.perform(post("/employees/create").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/employees/edit/{id}", employeeId).header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/employees/delete/{id}", employeeId).header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/employees/reorder").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":\"%s\",\"displayOrder\":3}]}".formatted(employeeId)))
                .andExpect(status().isForbidden());

        // Service menu mutations
        mockMvc.perform(post("/services/create").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pedicure\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/services/edit/{id}", serviceId).header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/services/delete/{id}", serviceId).header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isForbidden());

        // User management
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"extra\",\"password\":\"secret-pass\",\"role\":\"staff\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_isAllowedOnOwnerLevelMutations() throws Exception {
        mockMvc.perform(post("/employees/create").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/services/create").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pedicure\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/employees/reorder").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":\"%s\",\"displayOrder\":3}]}".formatted(employeeId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newstaff\",\"password\":\"secret-pass\",\"role\":\"staff\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createUser_rejectsDroppedAdminRole() throws Exception {
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminuser\",\"password\":\"secret-pass\",\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staff_isAllowedOnFrontDeskWorkAndReads() throws Exception {
        // Reads needed for booking
        mockMvc.perform(get("/employees/").header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/services/").header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isOk());

        // Front-desk client + appointment work
        mockMvc.perform(post("/clients/create").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Walk In\",\"phoneNumber\":\"330-555-7777\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/appointments/create").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeId, serviceId)))
                .andExpect(status().isCreated());
    }

    private String appointmentBody(UUID employeeId, UUID serviceId) {
        return """
                {
                  "customerName": "Walk In",
                  "employeeId": "%s",
                  "serviceIds": ["%s"],
                  "phoneNumber": "330-555-8888",
                  "startsAt": "2026-04-10T12:00:00-04:00",
                  "endsAt": "2026-04-10T13:00:00-04:00",
                  "showedUp": false
                }
                """.formatted(employeeId, serviceId);
    }

    private UUID insertEmployee(UUID organizationId, String name, String color) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color) values (?, ?, ?) returning id",
                UUID.class, organizationId, name, color);
    }

    private UUID insertService(UUID organizationId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into services (organization_id, name) values (?, ?) returning id",
                UUID.class, organizationId, name);
    }
}
