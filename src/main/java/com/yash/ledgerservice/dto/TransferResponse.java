package com.yash.ledgerservice.dto;

import com.yash.ledgerservice.model.Transfer;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        Long transferId,
        UUID journalId,
        Long fromAccountId,
        Long toAccountId,
        long amount,
        String currency,
        String status,
        Instant createdAt
) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getJournalId(),
                transfer.getFromAccount().getId(),
                transfer.getToAccount().getId(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus().name(),
                transfer.getCreatedAt());
    }
}