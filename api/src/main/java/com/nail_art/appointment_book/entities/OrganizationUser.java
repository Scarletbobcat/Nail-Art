package com.nail_art.appointment_book.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_users")
public class OrganizationUser {
    @EmbeddedId
    private OrganizationUserId id;

    @Column(name = "role", nullable = false)
    private String role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public OrganizationUserId getId() {
        return id;
    }

    public void setId(OrganizationUserId id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return id == null ? null : id.getOrganizationId();
    }

    public UUID getUserId() {
        return id == null ? null : id.getUserId();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
