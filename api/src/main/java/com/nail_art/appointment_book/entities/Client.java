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

    @NotNull
    private long id;

    @NotNull
    private String name;

    @Pattern(regexp = "^((\\+\\d{1,2}\\s?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4})?$", message = "Not a valid phone number")
    private String phoneNumber;

    private List<Long> appointmentIds;
}
