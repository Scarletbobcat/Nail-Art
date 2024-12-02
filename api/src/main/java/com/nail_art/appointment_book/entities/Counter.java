package com.nail_art.appointment_book.entities;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Setter
@Getter
@Data
@Document(collection = "Counters")
public class Counter {
    @Id
    private String _id;

    // Manually added getters and setters
    private long sequence;
    private String collectionName;

    public Counter(long sequence, String collectionName) {
        this.sequence = sequence;
        this.collectionName = collectionName;
    }

}