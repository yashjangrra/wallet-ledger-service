package com.yash.ledgerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank(message = "ownerName is required")
        @Size(max = 100, message = "ownerName must be at most 100 characters")
        String ownerName,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "(?i)^[A-Z]{3}$", message = "currency must be a 3-letter code, e.g. USD, INR, EUR")
        String currency
) {
    public CreateAccountRequest {
        ownerName = ownerName != null ? ownerName.trim() : null;
        currency = currency != null ? currency.trim().toUpperCase() : null;
    }
}
