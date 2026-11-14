package com.yash.ledgerservice.service;

import com.yash.ledgerservice.dto.ReconciliationReport;
import com.yash.ledgerservice.repository.LedgerEntryRepository;
import com.yash.ledgerservice.repository.TransferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final int MAX_JOURNALS_REPORTED = 20;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;

    public ReconciliationService(LedgerEntryRepository ledgerEntryRepository,
                                 TransferRepository transferRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transferRepository = transferRepository;
    }

    // One consistent snapshot for all checks, so a transfer committing mid-report
    // cannot make the checks disagree with each other.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport run() {
        long totalEntries = ledgerEntryRepository.count();
        long netSum = ledgerEntryRepository.netSumOfAllEntries();
        List<UUID> unbalanced = ledgerEntryRepository.findUnbalancedJournals(
                PageRequest.of(0, MAX_JOURNALS_REPORTED));
        long transfersWithoutTwoEntries = transferRepository.countTransfersWithoutExactlyTwoEntries();

        boolean healthy = netSum == 0 && unbalanced.isEmpty() && transfersWithoutTwoEntries == 0;

        return new ReconciliationReport(
                healthy, totalEntries, netSum, unbalanced, transfersWithoutTwoEntries, Instant.now());
    }
}