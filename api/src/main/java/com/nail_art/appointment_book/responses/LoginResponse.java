package com.nail_art.appointment_book.responses;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;

    private long expiresIn;

    public void setToken(String token) {
        this.token = token;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}