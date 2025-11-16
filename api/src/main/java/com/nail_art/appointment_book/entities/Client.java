package com.nail_art.appointment_book.entities;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "Clients")
public class Client {
    @Id
    private String _id;

    private Long id;

    @NotNull
    private String name;

    @Pattern(regexp = "^((\\+\\d{1,2}\\s?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4})?$", message = "Not a valid phone number")
    private String phoneNumber;

    private List<Long> appointmentIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public List<Long> getAppointmentIds() {
        return appointmentIds;
    }

    public void setAppointmentIds(List<Long> appointmentIds) {
        this.appointmentIds = appointmentIds;
    }
}
