package com.nail_art.appointment_book.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_settings")
public class OrganizationSettings {
    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "sms_reminders_enabled", nullable = false)
    private boolean smsRemindersEnabled;

    // Plaintext identifiers. The encrypted twilio_auth_token (bytea) is
    // intentionally left unmapped — it is read/written only through native
    // pgp_sym_* queries, never bound to a String by Hibernate.
    @Column(name = "twilio_account_sid")
    private String twilioAccountSid;

    @Column(name = "twilio_phone_number")
    private String twilioPhoneNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public boolean isSmsRemindersEnabled() {
        return smsRemindersEnabled;
    }

    public void setSmsRemindersEnabled(boolean smsRemindersEnabled) {
        this.smsRemindersEnabled = smsRemindersEnabled;
    }

    public String getTwilioAccountSid() {
        return twilioAccountSid;
    }

    public void setTwilioAccountSid(String twilioAccountSid) {
        this.twilioAccountSid = twilioAccountSid;
    }

    public String getTwilioPhoneNumber() {
        return twilioPhoneNumber;
    }

    public void setTwilioPhoneNumber(String twilioPhoneNumber) {
        this.twilioPhoneNumber = twilioPhoneNumber;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
