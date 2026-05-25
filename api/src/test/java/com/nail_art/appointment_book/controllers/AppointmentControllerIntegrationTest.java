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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentControllerIntegrationTest extends PostgresIntegrationTest {
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
        employeeA = insertEmployee(ownerA.organizationId(), "Anna");
        employeeB = insertEmployee(ownerB.organizationId(), "Bea");
        serviceA = insertService(ownerA.organizationId(), "Gel Manicure");
    }

    @Test
    void postPutDelete_happyPath_newShape() throws Exception {
        String body = appointmentBody(employeeA, serviceA, "Jane Doe", "330-555-1000", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");

        String response = mockMvc.perform(post("/appointments/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.startsAt").value("2026-04-10T10:00:00-04:00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID appointmentId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(put("/appointments/edit/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Jane Doe", "330-555-1000", "2026-04-10T11:00:00-04:00", "2026-04-10T12:00:00-04:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").value("2026-04-10T12:00:00-04:00"));

        mockMvc.perform(delete("/appointments/delete/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk());

        assertThat(archivedAt(appointmentId))
                .as("delete should soft-archive appointmentId=%s", appointmentId)
                .isNotNull();
    }

    @Test
    void postCreate_conflictingTime_returns409WithDescriptionBody() throws Exception {
        insertAppointment(ownerA.organizationId(), employeeA, "Existing", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        String path = "/appointments/create";

        mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Overlap", "330-555-1000", "2026-04-10T10:30:00-04:00", "2026-04-10T11:30:00-04:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.description").isString());
    }

    @Test
    void postCreate_validationErrors_returns400WithFieldMap() throws Exception {
        mockMvc.perform(post("/appointments/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeId": "%s",
                                  "serviceIds": ["%s"],
                                  "phoneNumber": "330-555-1000",
                                  "startsAt": "2026-04-10T10:00:00-04:00",
                                  "endsAt": "2026-04-10T11:00:00-04:00"
                                }
                                """.formatted(employeeA, serviceA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.customerName").isString());
    }

    @Test
    void crossOrgPUT_returns404_orgBRowUnchanged() throws Exception {
        UUID targetAppointment = insertAppointment(ownerB.organizationId(), employeeB, "Org B", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        String path = "/appointments/edit/" + targetAppointment;

        mockMvc.perform(put(path)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Mallory", "330-555-9999", "2026-04-10T12:00:00-04:00", "2026-04-10T13:00:00-04:00")))
                .andExpect(status().isNotFound());

        assertThat(customerName(targetAppointment))
                .as("method=PUT path=%s attackerOrg=%s targetOrg=%s status=404", path, ownerA.organizationId(), ownerB.organizationId())
                .isEqualTo("Org B");
    }

    @Test
    void crossOrgPOST_employeeIdMismatch_400Or409() throws Exception {
        String path = "/appointments/create";

        int status = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeB, serviceA, "Wrong Employee", "330-555-1000", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00")))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status)
                .as("method=POST path=%s attackerOrg=%s targetEmployeeOrg=%s targetEmployeeId=%s", path, ownerA.organizationId(), ownerB.organizationId(), employeeB)
                .isIn(400, 409);
    }

    @Test
    void crossOrgPOST_phoneMatchesOrgB_createsNewClientInOrgA() throws Exception {
        insertClient(ownerB.organizationId(), "Org B Client", "330-555-1234");

        mockMvc.perform(post("/appointments/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(employeeA, serviceA, "Org A Client", "330-555-1234", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00")))
                .andExpect(status().isCreated());

        assertThat(clientCount(ownerA.organizationId(), "330-555-1234"))
                .as("attackerOrg=%s targetOrg=%s phone=%s should create scoped org-A client, not link org-B client",
                        ownerA.organizationId(), ownerB.organizationId(), "330-555-1234")
                .isEqualTo(1);
    }

    @Test
    void crossOrgDELETE_returns404_orgBRowUnchanged() throws Exception {
        UUID targetAppointment = insertAppointment(ownerB.organizationId(), employeeB, "Org B", "2026-04-10T10:00:00-04:00", "2026-04-10T11:00:00-04:00");
        String path = "/appointments/delete/" + targetAppointment;

        mockMvc.perform(delete(path)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isNotFound());

        assertThat(archivedAt(targetAppointment))
                .as("method=DELETE path=%s attackerOrg=%s targetOrg=%s status=404", path, ownerA.organizationId(), ownerB.organizationId())
                .isNull();
    }

    private String appointmentBody(UUID employeeId, UUID serviceId, String customerName, String phoneNumber, String startsAt, String endsAt) {
        return """
                {
                  "customerName": "%s",
                  "employeeId": "%s",
                  "serviceIds": ["%s"],
                  "phoneNumber": "%s",
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "showedUp": false
                }
                """.formatted(customerName, employeeId, serviceId, phoneNumber, startsAt, endsAt);
    }

    private UUID insertEmployee(UUID organizationId, String name) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color) values (?, ?, '#111111') returning id",
                UUID.class,
                organizationId,
                name
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

    private UUID insertAppointment(UUID organizationId, UUID employeeId, String customerName, String startsAt, String endsAt) {
        return jdbcTemplate.queryForObject(
                """
                        insert into appointments (organization_id, employee_id, customer_name, starts_at, ends_at, phone_number)
                        values (?, ?, ?, ?::timestamptz, ?::timestamptz, '330-555-1000')
                        returning id
                        """,
                UUID.class,
                organizationId,
                employeeId,
                customerName,
                startsAt,
                endsAt
        );
    }

    private String customerName(UUID appointmentId) {
        return jdbcTemplate.queryForObject("select customer_name from appointments where id = ?", String.class, appointmentId);
    }

    private Object archivedAt(UUID appointmentId) {
        return jdbcTemplate.queryForObject("select archived_at from appointments where id = ?", Object.class, appointmentId);
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
