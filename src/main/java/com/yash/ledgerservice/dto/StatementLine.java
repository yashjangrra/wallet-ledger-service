package com.yash.ledgerservice.dto;

import java.time.Instant;
import java.util.UUID;

public record StatementLine(
        Long entryId,
        UUID journalId,
        String type,
        long amount,
        long runningBalance,
        Instant createdAt
) {}