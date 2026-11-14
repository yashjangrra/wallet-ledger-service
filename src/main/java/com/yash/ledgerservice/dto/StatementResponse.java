package com.yash.ledgerservice.dto;

import java.util.List;

public record StatementResponse(
        Long accountId,
        String currency,
        int page,
        int size,
        long totalEntries,
        long openingBalance,
        long closingBalance,
        List<StatementLine> lines
) {}