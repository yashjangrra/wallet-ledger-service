package com.yash.ledgerservice.service;

import com.yash.ledgerservice.dto.ReconciliationReport;
import com.yash.ledgerservice.repository.LedgerEntryRepository;
import com.yash.ledgerservice.repository.TransferRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        List<Object[]> currencyRows = ledgerEntryRepository.netSumByCurrency();
        Map<String, Long> netSumByCurrency = new LinkedHashMap<>();
        boolean allCurrenciesZero = true;
        for (Object[] row : currencyRows) {
            String currency = (String) row[0];
            Long sum = (Long) row[1];
            netSumByCurrency.put(currency, sum);
            if (sum != 0L) {
                allCurrenciesZero = false;
            }
        }

        List<UUID> unbalanced = ledgerEntryRepository.findUnbalancedJournals(
                PageRequest.of(0, MAX_JOURNALS_REPORTED));
        long transfersWithoutTwoEntries = transferRepository.countTransfersWithoutExactlyTwoEntries();

        boolean healthy = allCurrenciesZero && netSum == 0 && unbalanced.isEmpty() && transfersWithoutTwoEntries == 0;

        return new ReconciliationReport(
                healthy, totalEntries, netSum, netSumByCurrency, unbalanced, transfersWithoutTwoEntries, Instant.now());
    }
}