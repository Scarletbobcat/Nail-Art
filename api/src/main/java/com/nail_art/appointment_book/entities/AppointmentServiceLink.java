package com.nail_art.appointment_book.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

@Entity
@Table(name = "appointment_services")
public class AppointmentServiceLink {
    @EmbeddedId
    private AppointmentServiceLinkId id;

    @TenantId
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    public AppointmentServiceLink() {
    }

    public AppointmentServiceLink(UUID organizationId, UUID appointmentId, UUID serviceId) {
        this.organizationId = organizationId;
        this.id = new AppointmentServiceLinkId(appointmentId, serviceId);
    }

    public AppointmentServiceLinkId getId() {
        return id;
    }

    public void setId(AppointmentServiceLinkId id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getAppointmentId() {
        return id == null ? null : id.getAppointmentId();
    }

    public UUID getServiceId() {
        return id == null ? null : id.getServiceId();
    }
}
