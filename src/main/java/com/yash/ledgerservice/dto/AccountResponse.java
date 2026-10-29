package com.yash.ledgerservice.dto;

import com.yash.ledgerservice.model.Account;
import java.time.Instant;

public record AccountResponse(
        Long id,
        String ownerName,
        String currency,
        Instant createdAt
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getCreatedAt());
    }
}