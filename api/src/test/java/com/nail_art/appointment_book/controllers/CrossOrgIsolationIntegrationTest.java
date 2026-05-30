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

class CrossOrgIsolationIntegrationTest extends PostgresIntegrationTest {
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
    private UUID employeeA;
    private UUID employeeB;
    private UUID serviceA;
    private UUID serviceB;
    private UUID clientB;
    private UUID appointmentB;

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
        ownerA = identitySupport.seedIdentity("owner-a", "owner");
        ownerB = identitySupport.seedIdentity("owner-b", "owner");
        employeeA = insertEmployee(ownerA.organizationId(), "Anna", "#111111");
        employeeB = insertEmployee(ownerB.organizationId(), "Bea", "#222222");
        serviceA = insertService(ownerA.organizationId(), "Gel Manicure");
        serviceB = insertService(ownerB.organizationId(), "Waxing");
        insertClient(ownerA.organizationId(), "Org A Client", "330-555-1000");
        clientB = insertClient(ownerB.organizationId(), "Org B Client", "330-555-2000");
        insertAppointment(ownerA.organizationId(), employeeA, "Org A Appointment", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", "330-555-1000");
        appointmentB = insertAppointment(ownerB.organizationId(), employeeB, "Org B Appointment", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00", "330-555-2000");
    }

    @Test
    void readEndpoints_hideOrgBRowsFromOrgA() throws Exception {
        String token = identitySupport.bearer(ownerA);

        mockMvc.perform(get("/employees/").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(employeeB)).doesNotExist());
        mockMvc.perform(get("/services/").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(serviceB)).doesNotExist());
        mockMvc.perform(get("/clients/").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(clientB)).doesNotExist());
        mockMvc.perform(get("/appointments/date/2026-04-10").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(appointmentB)).doesNotExist());
        mockMvc.perform(get("/appointments/search/330-555-2000").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void writeEndpoints_blockOrgBIdsFromOrgA() throws Exception {
        String token = identitySupport.bearer(ownerA);

        mockMvc.perform(put("/employees/edit/{id}", employeeB)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mallory\",\"color\":\"#ffffff\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/services/edit/{id}", serviceB)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mallory\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/clients/edit/{id}", clientB)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mallory\",\"phoneNumber\":\"330-555-9999\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/appointments/edit/{id}", appointmentB)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Mallory", "330-555-9999")))
                .andExpect(status().isNotFound());

        assertThat(rowName("employees", employeeB))
                .as("attackerOrg=%s targetOrg=%s targetEmployee=%s", ownerA.organizationId(), ownerB.organizationId(), employeeB)
                .isEqualTo("Bea");
        assertThat(rowName("services", serviceB)).isEqualTo("Waxing");
        assertThat(customerName(appointmentB)).isEqualTo("Org B Appointment");
    }

    @Test
    void crossOrgAppointmentCreateWithOrgBEmployee_isStructurallyBlocked() throws Exception {
        int status = mockMvc.perform(post("/appointments/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeB, serviceA, "Wrong Employee", "330-555-3000")))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("method=POST path=/appointments/create attackerOrg=%s targetEmployeeOrg=%s employeeId=%s",
                        ownerA.organizationId(), ownerB.organizationId(), employeeB)
                .isIn(400, 409);
    }

    @Test
    void crossOrgAppointmentCreateWithOrgBPhone_createsOrgAClientOnly() throws Exception {
        mockMvc.perform(post("/appointments/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Org A New", "330-555-2000")))
                .andExpect(status().isCreated());

        assertThat(clientCount(ownerA.organizationId(), "330-555-2000"))
                .as("attackerOrg=%s targetOrg=%s phone=%s", ownerA.organizationId(), ownerB.organizationId(), "330-555-2000")
                .isEqualTo(1);
        assertThat(clientCount(ownerB.organizationId(), "330-555-2000")).isEqualTo(1);
    }

    @Test
    void readByIdEndpoints_hideOrgBRowsFromOrgA() throws Exception {
        String token = identitySupport.bearer(ownerA);

        // The documented Hibernate 6.5 PK-lookup leak class: a by-id read must go through
        // findScopedById, never a raw findById, so org A can never fetch org B's row by id.
        mockMvc.perform(get("/clients/{id}", clientB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/appointments/{id}", appointmentB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());

        // A nonexistent id is indistinguishable from a cross-org id (no existence oracle).
        mockMvc.perform(get("/clients/{id}", UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchAndNameReaders_hideOrgBRowsFromOrgA() throws Exception {
        String token = identitySupport.bearer(ownerA);

        // Derived ...ContainingIgnoreCase / name finders rely entirely on the @TenantId filter.
        mockMvc.perform(get("/employees/name/{name}", "Bea").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(employeeB)).doesNotExist());
        mockMvc.perform(get("/services/name/{name}", "Waxing").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(serviceB)).doesNotExist());
        mockMvc.perform(get("/appointments/search/{phone}", "330-555-2000").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Paged readers with org B's identifying values must surface zero org B rows.
        mockMvc.perform(get("/employees/").param("name", "Bea").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(employeeB)).doesNotExist());
        mockMvc.perform(get("/services/").param("name", "Waxing").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(serviceB)).doesNotExist());
        mockMvc.perform(get("/clients/").param("name", "Org B Client").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(clientB)).doesNotExist());
        mockMvc.perform(get("/clients/").param("phoneNumber", "330-555-2000").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(clientB)).doesNotExist());

        // /users/me must only ever describe the caller's own org.
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.id").value(ownerA.organizationId().toString()));
    }

    @Test
    void deleteEndpoints_blockOrgBIdsFromOrgA() throws Exception {
        String token = identitySupport.bearer(ownerA);

        mockMvc.perform(delete("/clients/delete/{id}", clientB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/employees/delete/{id}", employeeB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/services/delete/{id}", serviceB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/appointments/delete/{id}", appointmentB).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());

        // Org B rows survive untouched; the appointment soft-delete (archived_at) never fired.
        assertThat(rowExists("clients", clientB)).isTrue();
        assertThat(rowExists("employees", employeeB)).isTrue();
        assertThat(rowExists("services", serviceB)).isTrue();
        assertThat(rowExists("appointments", appointmentB)).isTrue();
        assertThat(appointmentArchivedAt(appointmentB)).isNull();
    }

    @Test
    void bodyOnlyAppointmentEdit_blocksOrgBId() throws Exception {
        String token = identitySupport.bearer(ownerA);

        // PUT /appointments/edit takes the id in the body — a separate code path from /edit/{id}.
        mockMvc.perform(put("/appointments/edit")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBodyWithId(appointmentB, employeeA, serviceA, "Mallory", "330-555-9999")))
                .andExpect(status().isNotFound());

        assertThat(customerName(appointmentB)).isEqualTo("Org B Appointment");
    }

    @Test
    void reorderEndpoint_cannotTouchOrgBEmployees() throws Exception {
        String token = identitySupport.bearer(ownerA);
        int orgBOrder = displayOrder(employeeB);
        int orgAOrder = displayOrder(employeeA);

        // Mixed payload: one of the caller's own employees plus org B's. reorder() uses an
        // inherited findAllById that is NOT @TenantId-filtered (the documented leak), so org B's
        // displayOrder could be rewritten. Security-correct outcome: the whole call fails and
        // org B's row is untouched.
        mockMvc.perform(post("/employees/reorder")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"id":"%s","displayOrder":5},{"id":"%s","displayOrder":999}]}
                                """.formatted(employeeA, employeeB)))
                .andExpect(status().isBadRequest());

        assertThat(displayOrder(employeeB))
                .as("org B employee displayOrder must be unchanged after a cross-org reorder attempt")
                .isEqualTo(orgBOrder);
        assertThat(displayOrder(employeeA))
                .as("org A employee displayOrder must be unchanged after the atomic reorder is rejected")
                .isEqualTo(orgAOrder);
    }

    private String appointmentBody(UUID employeeId, UUID serviceId, String customerName, String phoneNumber) {
        return """
                {
                  "customerName": "%s",
                  "employeeId": "%s",
                  "serviceIds": ["%s"],
                  "phoneNumber": "%s",
                  "startsAt": "2026-04-10T12:00:00-04:00",
                  "endsAt": "2026-04-10T13:00:00-04:00",
                  "showedUp": false
                }
                """.formatted(customerName, employeeId, serviceId, phoneNumber);
    }

    private String appointmentBodyWithId(UUID id, UUID employeeId, UUID serviceId, String customerName, String phoneNumber) {
        return """
                {
                  "id": "%s",
                  "customerName": "%s",
                  "employeeId": "%s",
                  "serviceIds": ["%s"],
                  "phoneNumber": "%s",
                  "startsAt": "2026-04-10T12:00:00-04:00",
                  "endsAt": "2026-04-10T13:00:00-04:00",
                  "showedUp": false
                }
                """.formatted(id, customerName, employeeId, serviceId, phoneNumber);
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

    private UUID insertService(UUID organizationId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into services (organization_id, name) values (?, ?) returning id",
                UUID.class,
                organizationId,
                name
        );
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

    private UUID insertAppointment(UUID organizationId, UUID employeeId, String customerName, String startsAt, String endsAt, String phoneNumber) {
        return jdbcTemplate.queryForObject(
                """
                        insert into appointments (organization_id, employee_id, customer_name, starts_at, ends_at, phone_number)
                        values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?)
                        returning id
                        """,
                UUID.class,
                organizationId,
                employeeId,
                customerName,
                startsAt,
                endsAt,
                phoneNumber
        );
    }

    private String rowName(String tableName, UUID id) {
        return jdbcTemplate.queryForObject("select name from " + tableName + " where id = ?", String.class, id);
    }

    private String customerName(UUID id) {
        return jdbcTemplate.queryForObject("select customer_name from appointments where id = ?", String.class, id);
    }

    private boolean rowExists(String tableName, UUID id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private java.time.OffsetDateTime appointmentArchivedAt(UUID id) {
        return jdbcTemplate.queryForObject(
                "select archived_at from appointments where id = ?", java.time.OffsetDateTime.class, id);
    }

    private int displayOrder(UUID employeeId) {
        Integer order = jdbcTemplate.queryForObject(
                "select display_order from employees where id = ?", Integer.class, employeeId);
        return order == null ? -1 : order;
    }

    private Integer clientCount(UUID organizationId, String phoneNumber) {
        return jdbcTemplate.queryForObject(
                "select count(*) from clients where organization_id = ? and phone_number = ?",
                Integer.class,
                organizationId,
                phoneNumber
        );
    }
}
