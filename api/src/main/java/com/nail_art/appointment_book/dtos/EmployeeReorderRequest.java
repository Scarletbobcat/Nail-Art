package com.nail_art.appointment_book.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record EmployeeReorderRequest(
        @NotEmpty(message = "items must not be empty")
        @Valid
        List<Item> items
) {
    public record Item(
            @NotNull(message = "id is required") UUID id,
            @NotNull(message = "displayOrder is required")
            @Min(value = 0, message = "displayOrder must be zero or greater")
            Integer displayOrder
    ) {
    }
}
