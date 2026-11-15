package com.yash.ledgerservice.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReconciliationReport(
        boolean healthy,
        long totalEntries,
        long netSumOfAllEntries,
        Map<String, Long> netSumByCurrency,
        List<UUID> unbalancedJournals,
        long transfersWithoutExactlyTwoEntries,
        Instant checkedAt
) {
    public ReconciliationReport(boolean healthy, long totalEntries, long netSumOfAllEntries,
                                List<UUID> unbalancedJournals, long transfersWithoutExactlyTwoEntries,
                                Instant checkedAt) {
        this(healthy, totalEntries, netSumOfAllEntries, Map.of(), unbalancedJournals, transfersWithoutExactlyTwoEntries, checkedAt);
    }
}