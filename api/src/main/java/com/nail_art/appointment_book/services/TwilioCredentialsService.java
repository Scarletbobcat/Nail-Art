package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.repositories.OrganizationSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns the pgcrypto symmetric key and is the only place it is bound into a
 * query. Callers (the SMS scheduler, the owner Settings endpoint) work in terms
 * of {@link TwilioCredentials} and never see the key.
 *
 * <p>The key comes from {@code app.encryption.key=${APP_ENCRYPTION_KEY}} with no
 * default, so a missing key fails app startup loudly rather than corrupting data.
 */
@Service
public class TwilioCredentialsService {
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final String encryptionKey;

    public TwilioCredentialsService(
            OrganizationSettingsRepository organizationSettingsRepository,
            @Value("${app.encryption.key}") String encryptionKey
    ) {
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.encryptionKey = encryptionKey;
    }

    /**
     * Decrypted credentials for one org. Returns a credentials object with null
     * fields when the org has no row or no config (so {@code isComplete()} is
     * false — a quiet skip). A wrong key makes the underlying decrypt RAISE,
     * which propagates here so the caller can fail loudly on key mismatch.
     */
    @Transactional(readOnly = true)
    public TwilioCredentials findForOrganization(UUID organizationId) {
        List<Object[]> rows = organizationSettingsRepository
                .findDecryptedCredentialsRaw(organizationId, encryptionKey);
        if (rows.isEmpty()) {
            return new TwilioCredentials(null, null, null);
        }
        Object[] row = rows.get(0);
        return new TwilioCredentials((String) row[0], (String) row[2], (String) row[1]);
    }

    /** Encrypt and persist the auth token for one org. */
    @Transactional
    public void saveAuthToken(UUID organizationId, String token) {
        organizationSettingsRepository.upsertEncryptedAuthToken(organizationId, token, encryptionKey);
    }
}
