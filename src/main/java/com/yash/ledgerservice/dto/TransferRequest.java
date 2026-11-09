package com.yash.ledgerservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull(message = "fromAccountId is required")
        Long fromAccountId,

        @NotNull(message = "toAccountId is required")
        Long toAccountId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Max(value = 10_000_000_000L, message = "amount exceeds the maximum allowed per transfer")
        Long amount   // in minor units (paise)
) {}