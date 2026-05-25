package com.nail_art.appointment_book.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AppointmentServiceLinkId implements Serializable {
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    public AppointmentServiceLinkId() {
    }

    public AppointmentServiceLinkId(UUID appointmentId, UUID serviceId) {
        this.appointmentId = appointmentId;
        this.serviceId = serviceId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(UUID serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppointmentServiceLinkId that)) {
            return false;
        }
        return Objects.equals(appointmentId, that.appointmentId)
                && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appointmentId, serviceId);
    }
}
