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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationSettingsIntegrationTest extends PostgresIntegrationTest {
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
    private String ownerToken;
    private String staffToken;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        identitySupport.resetIdentityTables();

        owner = identitySupport.seedIdentity("owner", "owner");
        SeededIdentity staff = new SeededIdentity(
                owner.organizationId(),
                identitySupport.seedUserInOrganization(owner.organizationId(), "staff", "staff"),
                "staff",
                "staff"
        );
        ownerToken = identitySupport.bearer(owner);
        staffToken = identitySupport.bearer(staff);
    }

    @Test
    void owner_get_returnsProfileAndUnconfiguredTwilioInitially() throws Exception {
        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(PostgresIdentityTestSupport.TEST_ORGANIZATION_NAME))
                .andExpect(jsonPath("$.timezone").value(PostgresIdentityTestSupport.TEST_TIMEZONE))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(false))
                .andExpect(jsonPath("$.twilioConfigured").value(false))
                .andExpect(jsonPath("$.twilioPhoneNumberMasked").doesNotExist());
    }

    @Test
    void staff_isForbiddenFromGetAndPut() throws Exception {
        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, staffToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, staffToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void owner_put_updatesProfileFields() throws Exception {
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Renamed Salon","businessPhone":"330-999-0000","timezone":"America/Chicago"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Salon"))
                .andExpect(jsonPath("$.businessPhone").value("330-999-0000"))
                .andExpect(jsonPath("$.timezone").value("America/Chicago"));

        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.name").value("Renamed Salon"))
                .andExpect(jsonPath("$.timezone").value("America/Chicago"));
    }

    @Test
    void put_enablingSmsWithIncompleteConfig_returns400_andLeavesItDisabled() throws Exception {
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsRemindersEnabled\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(false))
                .andExpect(jsonPath("$.twilioConfigured").value(false));
    }

    @Test
    void put_completeCredentialsAndEnabledInOneRequest_succeedsAndConfigures() throws Exception {
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "smsRemindersEnabled": true,
                                  "twilioAccountSid": "ACtest123",
                                  "twilioAuthToken": "super-secret-token",
                                  "twilioPhoneNumber": "+15551234567"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRemindersEnabled").value(true))
                .andExpect(jsonPath("$.twilioConfigured").value(true))
                .andExpect(jsonPath("$.twilioAccountSid").value("ACtest123"))
                .andExpect(jsonPath("$.twilioPhoneNumberMasked").value("•••• 4567"));
    }

    @Test
    void get_neverExposesTokenOrCiphertext() throws Exception {
        configureFully();

        String body = mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioConfigured").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("write-only token must never be serialized, plaintext or ciphertext")
                .doesNotContain("super-secret-token")
                .doesNotContain("twilioAuthToken");
        // raw ciphertext bytea begins with the PGP magic; ensure no token field leaked at all
        assertThat(body).doesNotContain("\\x");
    }

    @Test
    void put_profileOnly_preservesPreviouslyStoredToken() throws Exception {
        configureFully();

        // A profile-only PUT (no authToken) must not wipe the stored token.
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessPhone\":\"330-111-2222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioConfigured").value(true))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(true));
    }

    @Test
    void put_blankingTwilioPhone_autoDisablesToggle_noOrphanEnabledState() throws Exception {
        configureFully();

        // Clear the Twilio sending number -> config becomes incomplete -> toggle auto-off.
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"twilioPhoneNumber\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioConfigured").value(false))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(false));

        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.twilioConfigured").value(false))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(false));
    }

    @Test
    void writes_areScopedToPrincipalOrg_neverAnotherOrg() throws Exception {
        UUID otherOrg = identitySupport.createOrganization("Other Salon");
        jdbcTemplate.update("insert into organization_settings (organization_id) values (?)", otherOrg);

        configureFully();

        // The other org's settings row is untouched by this owner's writes.
        Boolean otherEnabled = jdbcTemplate.queryForObject(
                "select sms_reminders_enabled from organization_settings where organization_id = ?",
                Boolean.class, otherOrg);
        assertThat(otherEnabled).isFalse();
        Integer otherTokenCount = jdbcTemplate.queryForObject(
                "select count(*) from organization_settings where organization_id = ? and twilio_auth_token is not null",
                Integer.class, otherOrg);
        assertThat(otherTokenCount).isZero();
    }

    private void configureFully() throws Exception {
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "smsRemindersEnabled": true,
                                  "twilioAccountSid": "ACtest123",
                                  "twilioAuthToken": "super-secret-token",
                                  "twilioPhoneNumber": "+15551234567"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
