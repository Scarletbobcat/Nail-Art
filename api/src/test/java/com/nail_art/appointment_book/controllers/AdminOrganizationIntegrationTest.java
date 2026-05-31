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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private UUID ownerAId;
    private UUID staffAId;

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
        ownerAId = identitySupport.seedUserInOrganization(salonA, "ownerA", "owner");
        staffAId = identitySupport.seedUserInOrganization(salonA, "staffA", "staff");
        SeededIdentity ownerA = new SeededIdentity(salonA, ownerAId, "ownerA", "owner");
        SeededIdentity staffA = new SeededIdentity(salonA, staffAId, "staffA", "staff");
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

        // Capture the exact stored ciphertext. pgp_sym_encrypt is randomized, so a
        // re-encrypt (the bug we guard against) would change these bytes; an untouched
        // column keeps them identical. This proves preservation, not just "still non-null".
        String tokenBefore = jdbcTemplate.queryForObject(
                "select md5(twilio_auth_token::text) from organization_settings where organization_id = ?",
                String.class, salonB);

        // Re-send sid/phone with a blank token — must NOT wipe or rewrite the stored token.
        mockMvc.perform(put("/admin/organizations/{id}/twilio", salonB)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountSid\":\"ACtestsid\",\"phoneNumber\":\"+15555550111\",\"authToken\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));

        String tokenAfter = jdbcTemplate.queryForObject(
                "select md5(twilio_auth_token::text) from organization_settings where organization_id = ?",
                String.class, salonB);
        org.junit.jupiter.api.Assertions.assertEquals(tokenBefore, tokenAfter,
                "blank authToken must leave the stored ciphertext byte-for-byte unchanged");
    }

    @Test
    void adminToken_isForbiddenFromOwnerGatedSalonEndpoints() throws Exception {
        // platform_admin authority is not 'owner'/'staff': the admin cannot act on
        // owner-gated salon endpoints (defense beyond the org-less tenant scope).
        mockMvc.perform(post("/employees/create").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"color\":\"#abcdef\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/users").header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"secret-pass\",\"role\":\"staff\"}"))
                .andExpect(status().isForbidden());
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
    void adminCreatesSalon_withMarkerServiceAndSettings_andOwnerCanSignIn() throws Exception {
        String body = mockMvc.perform(post("/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fresh Salon\",\"timezone\":\"America/Chicago\","
                                + "\"ownerUsername\":\"freshowner\",\"ownerPassword\":\"secret-pass\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fresh Salon"))
                .andExpect(jsonPath("$.ownerUsername").value("freshowner"))
                .andReturn().getResponse().getContentAsString();
        UUID freshSalonId = UUID.fromString(objectMapper.readTree(body).get("organizationId").asText());

        // Provisioned: an "Unavailable" marker service and a settings row (SMS off).
        Integer markerCount = jdbcTemplate.queryForObject(
                "select count(*) from services where organization_id = ? and is_unavailability_marker = true and name = 'Unavailable'",
                Integer.class, freshSalonId);
        org.junit.jupiter.api.Assertions.assertEquals(1, markerCount);
        Boolean smsEnabled = jdbcTemplate.queryForObject(
                "select sms_reminders_enabled from organization_settings where organization_id = ?",
                Boolean.class, freshSalonId);
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.FALSE, smsEnabled);

        // Appears in the admin list (now 3 salons) and the new owner can authenticate.
        mockMvc.perform(get("/admin/organizations").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"freshowner\",\"password\":\"secret-pass\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void createSalon_duplicateOrgName_isRejected() throws Exception {
        mockMvc.perform(post("/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Salon A\",\"ownerUsername\":\"someone\",\"ownerPassword\":\"secret-pass\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createSalon_duplicateOwnerUsername_rollsBackTheNewOrg() throws Exception {
        mockMvc.perform(post("/admin/organizations")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rollback Salon\",\"ownerUsername\":\"ownerA\",\"ownerPassword\":\"secret-pass\"}"))
                .andExpect(status().isConflict());

        Integer orgCount = jdbcTemplate.queryForObject(
                "select count(*) from organizations where name = 'Rollback Salon'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, orgCount, "the org must not persist when owner creation fails");
    }

    @Test
    void ownerAndStaff_areForbiddenFromCreatingSalons() throws Exception {
        for (String token : new String[]{ownerAToken, staffAToken}) {
            mockMvc.perform(post("/admin/organizations")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"X\",\"ownerUsername\":\"y\",\"ownerPassword\":\"z\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    private void login(String username, String password, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(expected);
    }

    @Test
    void adminListsSalonUsers() throws Exception {
        mockMvc.perform(get("/admin/organizations/{id}/users", salonA).header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.username=='ownerA')].role").value("owner"))
                .andExpect(jsonPath("$[?(@.username=='staffA')].role").value("staff"));
    }

    @Test
    void adminChangesPassword_oldFailsNewWorks() throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}/users/{userId}", salonA, ownerAId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"brand-new-pass\"}"))
                .andExpect(status().isOk());

        login("ownerA", PostgresIdentityTestSupport.TEST_PASSWORD, status().isUnauthorized());
        login("ownerA", "brand-new-pass", status().isOk());
    }

    @Test
    void adminChangesUsername_newUsernameAuthenticates() throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}/users/{userId}", salonA, ownerAId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner-renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner-renamed"));

        login("owner-renamed", PostgresIdentityTestSupport.TEST_PASSWORD, status().isOk());
    }

    @Test
    void changeUsername_toAnExistingUsername_isRejected() throws Exception {
        mockMvc.perform(put("/admin/organizations/{id}/users/{userId}", salonA, ownerAId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"staffA\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUser_notAMemberOfThatSalon_isRejected() throws Exception {
        // ownerA belongs to Salon A, not Salon B — editing them via Salon B must fail.
        mockMvc.perform(put("/admin/organizations/{id}/users/{userId}", salonB, ownerAId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerAndStaff_areForbiddenFromUserManagement() throws Exception {
        for (String token : new String[]{ownerAToken, staffAToken}) {
            mockMvc.perform(get("/admin/organizations/{id}/users", salonA).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/admin/organizations/{id}/users/{userId}", salonA, staffAId)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"x\"}"))
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
