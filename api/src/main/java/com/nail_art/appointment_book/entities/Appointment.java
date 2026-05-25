package com.nail_art.appointment_book.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "client_id")
    private UUID clientId;

    @NotNull
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @NotNull
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @NotNull
    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @NotBlank(message = "Name is required")
    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Pattern(regexp = "^((\\+\\d{1,2}\\s?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4})?$", message = "Not a valid phone number")
    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "reminder_sent_at")
    private OffsetDateTime reminderSentAt;

    @Column(name = "showed_up", nullable = false)
    private Boolean showedUp = false;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Transient
    private List<UUID> serviceIds = new ArrayList<>();

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

    public UUID getClientId() {
        return clientId;
    }

    public void setClientId(UUID clientId) {
        this.clientId = clientId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(OffsetDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(OffsetDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    @JsonProperty("name")
    public String getName() {
        return customerName;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.customerName = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public OffsetDateTime getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(OffsetDateTime reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
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

    public Boolean getShowedUp() {
        return showedUp;
    }

    public void setShowedUp(Boolean showedUp) {
        this.showedUp = showedUp;
    }

    public List<UUID> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<UUID> serviceIds) {
        this.serviceIds = serviceIds == null ? new ArrayList<>() : new ArrayList<>(serviceIds);
    }

    @JsonProperty("services")
    public List<UUID> getServices() {
        return getServiceIds();
    }

    @JsonProperty("services")
    public void setServices(List<UUID> services) {
        setServiceIds(services);
    }

    public Boolean getReminderSent() {
        return reminderSentAt != null;
    }

    public void setReminderSent(Boolean reminderSent) {
        if (Boolean.TRUE.equals(reminderSent) && reminderSentAt == null) {
            reminderSentAt = OffsetDateTime.now();
        } else if (!Boolean.TRUE.equals(reminderSent)) {
            reminderSentAt = null;
        }
    }

    public String getDate() {
        return startsAt == null ? null : startsAt.toLocalDate().toString();
    }

    public String getStartTime() {
        return startsAt == null ? null : "T" + startsAt.toLocalTime().withNano(0);
    }

    public String getEndTime() {
        return endsAt == null ? null : "T" + endsAt.toLocalTime().withNano(0);
    }
}
