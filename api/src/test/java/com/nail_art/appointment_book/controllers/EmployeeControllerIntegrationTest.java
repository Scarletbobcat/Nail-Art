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
        ownerA = identitySupport.seedIdentity("owner-a", "owner", "Org A");
        ownerB = identitySupport.seedIdentity("owner-b", "owner", "Org B");
    }

    @Test
    void postCreate_validBody_returns201() throws Exception {
        mockMvc.perform(post("/employees/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice",
                                  "color": "#ff0000",
                                  "displayOrder": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.displayOrder").value(4));
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
    void getEmployees_ordersByDisplayOrderBeforeName() throws Exception {
        UUID zara = insertEmployee(ownerA.organizationId(), "Zara", "#111111", 0);
        UUID alice = insertEmployee(ownerA.organizationId(), "Alice", "#222222", 1);
        UUID bea = insertEmployee(ownerA.organizationId(), "Bea", "#333333", 2);

        mockMvc.perform(get("/employees/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(zara.toString()))
                .andExpect(jsonPath("$.content[1].id").value(alice.toString()))
                .andExpect(jsonPath("$.content[2].id").value(bea.toString()));
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
                                  "color": "#00ff00",
                                  "displayOrder": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.displayOrder").value(2));

        mockMvc.perform(delete("/employees/delete/{id}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk());

        assertThat(employeeExists(employeeId))
                .as("operation=end-to-end delete activeOrg=%s employeeId=%s", ownerA.organizationId(), employeeId)
                .isFalse();
    }

    @Test
    void postReorder_fullRotation_succeedsViaDeferredConstraint() throws Exception {
        UUID a = insertEmployee(ownerA.organizationId(), "Anna", "#111", 0);
        UUID b = insertEmployee(ownerA.organizationId(), "Bea", "#222", 1);
        UUID c = insertEmployee(ownerA.organizationId(), "Cora", "#333", 2);

        String payload = """
                {
                  "items": [
                    { "id": "%s", "displayOrder": 2 },
                    { "id": "%s", "displayOrder": 0 },
                    { "id": "%s", "displayOrder": 1 }
                  ]
                }
                """.formatted(a, b, c);

        mockMvc.perform(post("/employees/reorder")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(b.toString()))
                .andExpect(jsonPath("$[1].id").value(c.toString()))
                .andExpect(jsonPath("$[2].id").value(a.toString()));

        assertThat(displayOrderOf(a))
                .as("post-reorder display_order on full rotation orgA=%s", ownerA.organizationId())
                .isEqualTo(2);
        assertThat(displayOrderOf(b)).isEqualTo(0);
        assertThat(displayOrderOf(c)).isEqualTo(1);
    }

    @Test
    void postReorder_crossTenantId_returns400AndLeavesDataUnchanged() throws Exception {
        UUID a = insertEmployee(ownerA.organizationId(), "Anna", "#111", 0);
        UUID foreign = insertEmployee(ownerB.organizationId(), "Bea", "#222", 0);

        String payload = """
                {
                  "items": [
                    { "id": "%s", "displayOrder": 1 },
                    { "id": "%s", "displayOrder": 0 }
                  ]
                }
                """.formatted(a, foreign);

        mockMvc.perform(post("/employees/reorder")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(displayOrderOf(a))
                .as("cross-tenant reorder must roll back; attackerOrg=%s targetOrg=%s",
                        ownerA.organizationId(), ownerB.organizationId())
                .isEqualTo(0);
        assertThat(displayOrderOf(foreign)).isEqualTo(0);
    }

    @Test
    void postReorder_emptyItems_returns400() throws Exception {
        mockMvc.perform(post("/employees/reorder")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postCreate_afterDelete_doesNotReuseGap() throws Exception {
        UUID a = insertEmployee(ownerA.organizationId(), "Anna", "#111", 0);
        insertEmployee(ownerA.organizationId(), "Bea", "#222", 1);
        insertEmployee(ownerA.organizationId(), "Cora", "#333", 2);

        mockMvc.perform(delete("/employees/delete/{id}", a)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk());

        String createResponse = mockMvc.perform(post("/employees/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Dan", "color": "#abcdef" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayOrder").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID danId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());
        assertThat(displayOrderOf(danId))
                .as("delete leaves a gap; new create still appends past max not into the gap orgA=%s",
                        ownerA.organizationId())
                .isEqualTo(3);
    }

    private Integer displayOrderOf(UUID employeeId) {
        return jdbcTemplate.queryForObject(
                "select display_order from employees where id = ?",
                Integer.class,
                employeeId
        );
    }

    private UUID insertEmployee(UUID organizationId, String name, String color) {
        return insertEmployee(organizationId, name, color, 0);
    }

    private UUID insertEmployee(UUID organizationId, String name, String color, int displayOrder) {
        return jdbcTemplate.queryForObject(
                "insert into employees (organization_id, name, color, display_order) values (?, ?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                color,
                displayOrder
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
