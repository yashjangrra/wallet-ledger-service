package com.yash.ledgerservice.dto;

public record BalanceResponse(
        Long accountId,
        String currency,
        long balanceInMinorUnits
) {}