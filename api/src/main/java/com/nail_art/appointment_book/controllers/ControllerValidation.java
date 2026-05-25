package com.nail_art.appointment_book.controllers;

import org.springframework.validation.BindingResult;

import java.util.HashMap;
import java.util.Map;

final class ControllerValidation {
    private ControllerValidation() {
    }

    static Map<String, String> fieldErrors(BindingResult result) {
        Map<String, String> errors = new HashMap<>();
        result.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return errors;
    }
}
