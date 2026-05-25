package com.nail_art.appointment_book.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Pattern(regexp = "^((\\+\\d{1,2}\\s?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4})?$", message = "Not a valid phone number")
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "sms_consent_status", nullable = false)
    private String smsConsentStatus = "unknown";

    @Column(name = "sms_consent_method")
    private String smsConsentMethod;

    @Column(name = "sms_consent_at")
    private OffsetDateTime smsConsentAt;

    @Column(name = "sms_blocked_reason")
    private String smsBlockedReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getSmsConsentStatus() {
        return smsConsentStatus;
    }

    public void setSmsConsentStatus(String smsConsentStatus) {
        this.smsConsentStatus = smsConsentStatus;
    }

    public String getSmsConsentMethod() {
        return smsConsentMethod;
    }

    public void setSmsConsentMethod(String smsConsentMethod) {
        this.smsConsentMethod = smsConsentMethod;
    }

    public OffsetDateTime getSmsConsentAt() {
        return smsConsentAt;
    }

    public void setSmsConsentAt(OffsetDateTime smsConsentAt) {
        this.smsConsentAt = smsConsentAt;
    }

    public String getSmsBlockedReason() {
        return smsBlockedReason;
    }

    public void setSmsBlockedReason(String smsBlockedReason) {
        this.smsBlockedReason = smsBlockedReason;
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
