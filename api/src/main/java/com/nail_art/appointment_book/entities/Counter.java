package com.nail_art.appointment_book.entities;

public class Counter {
    private long sequence;
    private String collectionName;

    public Counter(long sequence, String collectionName) {
        this.sequence = sequence;
        this.collectionName = collectionName;
    }

    // Manually added getters and setters
    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}