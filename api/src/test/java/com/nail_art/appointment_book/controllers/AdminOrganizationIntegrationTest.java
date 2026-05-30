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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform-admin operator console: list/read/update salons. An org-less admin can
 * manage any salon's config; owners/staff are locked out; and the admin — having
 * no tenant scope — sees no tenant-owned data through normal endpoints.
 */
class AdminOrganizationIntegrationTest extends PostgresIntegrationTest {
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
    private String adminToken;
    private String ownerAToken;
    private String staffAToken;
    private UUID salonA;
    private UUID salonB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        jdbcTemplate.update("delete from appointment_services");
        jdbcTemplate.update("delete from appointments");
        jdbcTemplate.update("delete from clients");
        jdbcTemplate.update("delete from services");
        jdbcTemplate.update("delete from employees");
        jdbcTemplate.update("delete from organization_settings");
        identitySupport.resetIdentityTables();

        UUID adminId = identitySupport.seedPlatformAdmin("operator");
        adminToken = identitySupport.adminBearer(adminId);

        salonA = identitySupport.createOrganization("Salon A");
        SeededIdentity ownerA = new SeededIdentity(
                salonA, identitySupport.seedUserInOrganization(salonA, "ownerA", "owner"), "ownerA", "owner");
        SeededIdentity staffA = new SeededIdentity(
                salonA, identitySupport.seedUserInOrganization(salonA, "staffA", "staff"), "staffA", "staff");
        ownerAToken = identitySupport.bearer(ownerA);
        staffAToken = identitySupport.bearer(staffA);

        salonB = identitySupport.createOrganization("Salon B");
    }

    @Test
    void adminListsAllSalons() throws Exception {
        mockMvc.perform(get("/admin/organizations").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name=='Salon A')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Salon B')]").exists())
                .andExpect(jsonPath("$[?(@.name=='Salon A')].twilioConfigured").value(false));
    }

    @Test
    void adminUpdatesOneSalonProfile_leavingOthersUntouched() throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salon B Renamed\",\"timezone\":\"America/Chicago\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salon B Renamed"))
                .andExpect(jsonPath("$.timezone").value("America/Chicago"));

        mockMvc.perform(get("/admin/organizations/{id}", salonA).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Salon A"));
    }

    @Test
    void enableSms_withoutTwilio_isRejected() throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsRemindersEnabled\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerAndStaff_areForbiddenFromAdminEndpoints() throws Exception {
        for (String token : new String[]{ownerAToken, staffAToken}) {
            mockMvc.perform(get("/admin/organizations").header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/organizations/{id}", salonA).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/admin/organizations/{id}", salonA)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Hijacked\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    private void configureTwilio(UUID salon, String sid, String phone, String token) throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}/twilio", salon)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountSid\":\"%s\",\"phoneNumber\":\"%s\",\"authToken\":\"%s\"}"
                                .formatted(sid, phone, token)))
                .andExpect(status().isOk());
    }

    @Test
    void adminConfiguresTwilio_thenReadsBackWithoutToken() throws Exception {
        configureTwilio(salonB, "ACtestsid", "+15555550111", "super-secret-token");

        String body = mockMvc.perform(get("/admin/organizations/{id}/twilio", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.accountSid").value("ACtestsid"))
                .andExpect(jsonPath("$.phoneNumber").value("+15555550111"))
                .andReturn().getResponse().getContentAsString();

        // R11: the auth token is write-only — never present in any read.
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("super-secret-token"), "auth token must never be returned");
    }

    @Test
    void smsToggle_enableable_onceTwilioConfigured() throws Exception {
        configureTwilio(salonB, "ACtestsid", "+15555550111", "super-secret-token");

        mockMvc.perform(put("/admin/organizations/{id}", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsRemindersEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRemindersEnabled").value(true))
                .andExpect(jsonPath("$.twilioConfigured").value(true));
    }

    @Test
    void blankToken_preservesStoredToken() throws Exception {
        configureTwilio(salonB, "ACtestsid", "+15555550111", "super-secret-token");

        // Re-send sid/phone with a blank token — must NOT wipe the stored token.
        mockMvc.perform(put("/admin/organizations/{id}/twilio", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountSid\":\"ACtestsid\",\"phoneNumber\":\"+15555550111\",\"authToken\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void ownerAndStaff_areForbiddenFromTwilioEndpoints() throws Exception {
        for (String token : new String[]{ownerAToken, staffAToken}) {
            mockMvc.perform(get("/admin/organizations/{id}/twilio", salonA).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/admin/organizations/{id}/twilio", salonA)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountSid\":\"x\",\"phoneNumber\":\"y\",\"authToken\":\"z\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void orglessAdmin_seesNoTenantOwnedData() throws Exception {
        // Salon A has an employee; the org-less admin (no tenant scope) must see none
        // through the normal tenant-scoped endpoint — sentinel-empty, never a leak.
        jdbcTemplate.update(
                "insert into employees (organization_id, name, color) values (?, ?, ?)",
                salonA, "Anna", "#111111");

        // /employees/ returns a Page; the org-less admin's tenant scope is the
        // sentinel, so its content is empty even though Salon A has an employee.
        mockMvc.perform(get("/employees/").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
