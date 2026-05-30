package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.services.TwilioCredentials;
import com.nail_art.appointment_book.services.TwilioCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationSettingsRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private TwilioCredentialsService twilioCredentialsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID orgA;
    private UUID orgB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from organization_settings");
        jdbcTemplate.update("delete from organization_users");
        jdbcTemplate.update("delete from users");
        jdbcTemplate.update("delete from organizations");
        orgA = insertOrganization("Org A");
        orgB = insertOrganization("Org B");
    }

    @Test
    void saveAuthToken_thenRead_roundTripsDecryptedTokenAndPlaintextFields() {
        twilioCredentialsService.saveAuthToken(orgA, "AUTH-TOKEN-abc123");
        setSidAndPhone(orgA, "ACsid-orgA", "+15555550111");

        TwilioCredentials creds = twilioCredentialsService.findForOrganization(orgA);

        assertThat(creds.accountSid()).isEqualTo("ACsid-orgA");
        assertThat(creds.authToken()).isEqualTo("AUTH-TOKEN-abc123");
        assertThat(creds.phoneNumber()).isEqualTo("+15555550111");
        assertThat(creds.isComplete()).isTrue();
    }

    @Test
    void storedAuthToken_isCiphertext_notPlaintext() {
        twilioCredentialsService.saveAuthToken(orgA, "AUTH-TOKEN-abc123");

        byte[] stored = jdbcTemplate.queryForObject(
                "select twilio_auth_token from organization_settings where organization_id = ?",
                byte[].class,
                orgA
        );

        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.UTF_8))
                .as("auth token must be encrypted at rest, never stored as plaintext")
                .doesNotContain("AUTH-TOKEN-abc123");
    }

    @Test
    void noSettingsRow_readsBackEmptyIncompleteCredentials() {
        // orgA has no organization_settings row at all.
        TwilioCredentials creds = twilioCredentialsService.findForOrganization(orgA);

        assertThat(creds.accountSid()).isNull();
        assertThat(creds.authToken()).isNull();
        assertThat(creds.phoneNumber()).isNull();
        assertThat(creds.isComplete()).isFalse();
    }

    @Test
    void settingsRowWithoutToken_isIncomplete_andDoesNotRaise() {
        insertSettingsRow(orgA);
        setSidAndPhone(orgA, "ACsid-orgA", "+15555550111");

        // Null token column must yield a null token (a quiet skip), NOT a decrypt error.
        TwilioCredentials creds = twilioCredentialsService.findForOrganization(orgA);

        assertThat(creds.accountSid()).isEqualTo("ACsid-orgA");
        assertThat(creds.authToken()).isNull();
        assertThat(creds.isComplete()).isFalse();
    }

    @Test
    void read_isScopedToOrganizationId_doesNotLeakAcrossTenants() {
        twilioCredentialsService.saveAuthToken(orgA, "AUTH-TOKEN-orgA");
        insertSettingsRow(orgB);

        // organization_settings has no @TenantId; the query must scope by the
        // explicit :organizationId bind, so orgB never sees orgA's token.
        TwilioCredentials orgBCreds = twilioCredentialsService.findForOrganization(orgB);

        assertThat(orgBCreds.authToken()).isNull();
    }

    @Test
    void read_withTokenEncryptedUnderDifferentKey_raisesRatherThanReturningNull() {
        insertSettingsRow(orgA);
        // Encrypt the token under a DIFFERENT key than the app's configured key.
        jdbcTemplate.update(
                "update organization_settings set twilio_auth_token = pgp_sym_encrypt(?, ?) where organization_id = ?",
                "AUTH-TOKEN-abc123",
                "a-totally-different-key",
                orgA
        );

        // A key mismatch must surface as a raised error (caller fails loudly),
        // never as a silent null that looks like "no creds configured".
        assertThatThrownBy(() -> twilioCredentialsService.findForOrganization(orgA))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertOrganization(String name) {
        return jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, '+15555550101', 'America/New_York') returning id",
                UUID.class,
                name
        );
    }

    private void insertSettingsRow(UUID organizationId) {
        jdbcTemplate.update(
                "insert into organization_settings (organization_id) values (?)",
                organizationId
        );
    }

    private void setSidAndPhone(UUID organizationId, String sid, String phone) {
        jdbcTemplate.update(
                "update organization_settings set twilio_account_sid = ?, twilio_phone_number = ? where organization_id = ?",
                sid,
                phone,
                organizationId
        );
    }
}
