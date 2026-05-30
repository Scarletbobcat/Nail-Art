package com.nail_art.appointment_book.repositories;

import com.nail_art.appointment_book.entities.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {
    List<OrganizationSettings> findBySmsRemindersEnabledTrue();

    // organization_settings has no @TenantId discriminator, so these native
    // queries MUST scope by :organizationId explicitly — Hibernate applies no
    // automatic tenant filter here.

    /**
     * Encrypt and store the auth token for one org. Upsert so a missing
     * settings row is created rather than silently writing zero rows; only the
     * token column is touched on conflict, preserving sid/phone/sms flag.
     * pgcrypto signature is pinned (no options string) — Java, Python, and the
     * cutover script must all use {@code pgp_sym_encrypt(token, key)}.
     */
    @Modifying
    @Query(value = """
            insert into organization_settings (organization_id, twilio_auth_token)
            values (:organizationId, pgp_sym_encrypt(cast(:token as text), cast(:key as text)))
            on conflict (organization_id)
            do update set twilio_auth_token = excluded.twilio_auth_token,
                          updated_at = now()
            """, nativeQuery = true)
    void upsertEncryptedAuthToken(
            @Param("organizationId") UUID organizationId,
            @Param("token") String token,
            @Param("key") String key
    );

    /**
     * Read sid, phone, and the DECRYPTED token for one org as a raw row
     * {@code [accountSid, phoneNumber, authToken]}. A wrong/mismatched key makes
     * {@code pgp_sym_decrypt} RAISE (not return null); a null token column simply
     * yields a null token. Callers must treat those two cases oppositely.
     */
    @Query(value = """
            select twilio_account_sid,
                   twilio_phone_number,
                   pgp_sym_decrypt(twilio_auth_token, cast(:key as text))
            from organization_settings
            where organization_id = :organizationId
            """, nativeQuery = true)
    List<Object[]> findDecryptedCredentialsRaw(
            @Param("organizationId") UUID organizationId,
            @Param("key") String key
    );

    /**
     * Whether this org has a stored (encrypted) auth token, WITHOUT decrypting it —
     * used to compute "twilioConfigured" and the enable-gate without ever touching
     * the key or the plaintext. Null when the org has no settings row.
     */
    @Query(value = """
            select twilio_auth_token is not null
            from organization_settings
            where organization_id = :organizationId
            """, nativeQuery = true)
    Boolean authTokenPresent(@Param("organizationId") UUID organizationId);
}
