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

    @Value("${app.encryption.key}")
    private String encryptionKey;

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
                .andExpect(jsonPath("$.twilioConfigured").value(false));
    }

    @Test
    void get_neverExposesTwilioCredentials() throws Exception {
        operatorConfigureTwilio(owner.organizationId());

        String body = mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioConfigured").value(true))
                .andReturn().getResponse().getContentAsString();

        // Owners never see Twilio identifiers or the token over the API.
        assertThat(body)
                .doesNotContain("twilioAccountSid")
                .doesNotContain("twilioAuthToken")
                .doesNotContain("twilioPhoneNumber")
                .doesNotContain("ACoperator")
                .doesNotContain("operator-token");
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
    void put_enablingSmsWhenTwilioNotConfigured_returns400() throws Exception {
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsRemindersEnabled\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(jsonPath("$.smsRemindersEnabled").value(false))
                .andExpect(jsonPath("$.twilioConfigured").value(false));
    }

    @Test
    void put_enablingSmsWhenOperatorHasConfiguredTwilio_succeeds() throws Exception {
        operatorConfigureTwilio(owner.organizationId());

        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsRemindersEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRemindersEnabled").value(true))
                .andExpect(jsonPath("$.twilioConfigured").value(true));
    }

    @Test
    void put_profileOnly_doesNotClobberStoredSmsFlag_evenWhenNotYetConfigured() throws Exception {
        // Legacy state: the salon's reminder flag is on, but Twilio creds are not
        // yet in the DB (cutover pending). A profile-only edit must leave the flag
        // intact so reminders resume once the operator loads credentials.
        seedSettingsFlag(owner.organizationId(), true);

        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessPhone\":\"330-111-2222\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.smsRemindersEnabled").value(true))
                .andExpect(jsonPath("$.twilioConfigured").value(false));

        Boolean stored = jdbcTemplate.queryForObject(
                "select sms_reminders_enabled from organization_settings where organization_id = ?",
                Boolean.class, owner.organizationId());
        assertThat(stored).isTrue();
    }

    @Test
    void owner_put_cannotSetTwilioCredentials() throws Exception {
        // Even if a client sends Twilio fields, they are ignored (not in the DTO),
        // so the org stays unconfigured and the toggle cannot be turned on.
        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "twilioAccountSid": "ACsneaky",
                                  "twilioAuthToken": "sneaky-token",
                                  "twilioPhoneNumber": "+15550000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioConfigured").value(false));

        Integer tokenCount = jdbcTemplate.queryForObject(
                "select count(*) from organization_settings where organization_id = ? and twilio_auth_token is not null",
                Integer.class, owner.organizationId());
        assertThat(tokenCount).isZero();
    }

    @Test
    void writes_areScopedToPrincipalOrg_neverAnotherOrg() throws Exception {
        UUID otherOrg = identitySupport.createOrganization("Other Salon");
        jdbcTemplate.update("insert into organization_settings (organization_id) values (?)", otherOrg);

        mockMvc.perform(put("/organization").header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine Only\"}"))
                .andExpect(status().isOk());

        String otherName = jdbcTemplate.queryForObject(
                "select name from organizations where id = ?", String.class, otherOrg);
        assertThat(otherName).isEqualTo("Other Salon");
    }

    private void operatorConfigureTwilio(UUID organizationId) {
        jdbcTemplate.update(
                """
                insert into organization_settings
                    (organization_id, twilio_account_sid, twilio_phone_number, twilio_auth_token)
                values (?, 'ACoperator', '+15551234567', pgp_sym_encrypt('operator-token', ?))
                on conflict (organization_id) do update set
                    twilio_account_sid = excluded.twilio_account_sid,
                    twilio_phone_number = excluded.twilio_phone_number,
                    twilio_auth_token = excluded.twilio_auth_token
                """,
                organizationId, encryptionKey);
    }

    private void seedSettingsFlag(UUID organizationId, boolean enabled) {
        jdbcTemplate.update(
                """
                insert into organization_settings (organization_id, sms_reminders_enabled)
                values (?, ?)
                on conflict (organization_id) do update set sms_reminders_enabled = excluded.sms_reminders_enabled
                """,
                organizationId, enabled);
    }
}
