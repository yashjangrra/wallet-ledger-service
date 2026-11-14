package com.yash.ledgerservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReconciliationReport(
        boolean healthy,
        long totalEntries,
        long netSumOfAllEntries,
        List<UUID> unbalancedJournals,
        long transfersWithoutExactlyTwoEntries,
        Instant checkedAt
) {}