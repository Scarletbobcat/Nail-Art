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

class ServiceControllerIntegrationTest extends PostgresIntegrationTest {
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
        jdbcTemplate.update("delete from services");
        identitySupport.resetIdentityTables();
        ownerA = identitySupport.seedIdentity("owner-a", "owner");
        ownerB = identitySupport.seedIdentity("owner-b", "owner");
    }

    @Test
    void postCreate_validBody_returns201() throws Exception {
        mockMvc.perform(post("/services/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gel Manicure",
                                  "isUnavailabilityMarker": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Gel Manicure"))
                .andExpect(jsonPath("$.isUnavailabilityMarker").value(false));
    }

    @Test
    void getServices_responseIncludesIsUnavailabilityMarkerBoolean() throws Exception {
        UUID markerId = insertService(ownerA.organizationId(), "Unavailable", true);

        mockMvc.perform(get("/services/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(markerId.toString()))
                .andExpect(jsonPath("$.content[0].isUnavailabilityMarker").value(true));
    }

    @Test
    void getByName_returnsOrgScopedResults() throws Exception {
        UUID orgAServiceId = insertService(ownerA.organizationId(), "Gel Manicure", false);
        UUID orgBServiceId = insertService(ownerB.organizationId(), "Classic Pedicure", false);

        mockMvc.perform(get("/services/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .queryParam("name", "icure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(orgAServiceId.toString()));

        assertThat(orgBServiceId)
                .as("operation=controller service search activeOrg=%s seedOrgA=%s seedOrgB=%s orgAService=%s orgBService=%s",
                        ownerA.organizationId(), ownerA.organizationId(), ownerB.organizationId(), orgAServiceId, orgBServiceId)
                .isNotEqualTo(orgAServiceId);
    }

    @Test
    void postServicesCreate_caseInsensitiveDuplicateName_returns409() throws Exception {
        insertService(ownerA.organizationId(), "Gel Manicure", false);

        mockMvc.perform(post("/services/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "gel manicure"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void putServicesEdit_attemptToToggleMarkerFalse_leavesFlagUnchanged() throws Exception {
        UUID markerId = insertService(ownerA.organizationId(), "Unavailable", true);

        mockMvc.perform(put("/services/edit/{id}", markerId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Unavailable",
                                  "isUnavailabilityMarker": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isUnavailabilityMarker").value(true));
    }

    @Test
    void putServicesEdit_renameMarkerRow_flagStaysTrue() throws Exception {
        UUID markerId = insertService(ownerA.organizationId(), "Unavailable", true);

        mockMvc.perform(put("/services/edit/{id}", markerId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "PTO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PTO"))
                .andExpect(jsonPath("$.isUnavailabilityMarker").value(true));
    }

    @Test
    void deleteByIdOfAnotherOrg_returns404() throws Exception {
        UUID targetServiceId = insertService(ownerB.organizationId(), "Waxing", false);

        mockMvc.perform(delete("/services/delete/{id}", targetServiceId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isNotFound());

        assertThat(serviceExists(targetServiceId))
                .as("operation=cross-org service delete attackerOrg=%s targetStoredOrg=%s targetService=%s expectedStatus=404",
                        ownerA.organizationId(), ownerB.organizationId(), targetServiceId)
                .isTrue();
    }

    @Test
    void endToEnd_createListEditDelete_returnsExpectedStatuses() throws Exception {
        String createResponse = mockMvc.perform(post("/services/create")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Gel Manicure"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID serviceId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(get("/services/")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(serviceId.toString()));

        mockMvc.perform(put("/services/edit/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Deluxe Gel Manicure"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Deluxe Gel Manicure"));

        mockMvc.perform(delete("/services/delete/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(ownerA)))
                .andExpect(status().isOk());

        assertThat(serviceExists(serviceId))
                .as("operation=end-to-end service delete activeOrg=%s serviceId=%s", ownerA.organizationId(), serviceId)
                .isFalse();
    }

    private UUID insertService(UUID organizationId, String name, boolean unavailabilityMarker) {
        return jdbcTemplate.queryForObject(
                "insert into services (organization_id, name, is_unavailability_marker) values (?, ?, ?) returning id",
                UUID.class,
                organizationId,
                name,
                unavailabilityMarker
        );
    }

    private boolean serviceExists(UUID serviceId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from services where id = ?",
                Integer.class,
                serviceId
        );
        return count != null && count == 1;
    }
}
