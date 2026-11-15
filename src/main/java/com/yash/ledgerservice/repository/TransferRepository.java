package com.yash.ledgerservice.repository;

import com.yash.ledgerservice.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    java.util.List<Transfer> findTop50ByOrderByIdDesc();

    java.util.List<Transfer> findByFromAccountIdOrToAccountIdOrderByIdDesc(Long fromAccountId, Long toAccountId);

    // Reconciliation check 3: every transfer must have exactly one DEBIT and one CREDIT entry.
    @Query(value = """
            select count(*) from transfers t
            where (select count(*) from ledger_entries e where e.journal_id = t.journal_id) <> 2
            """, nativeQuery = true)
    long countTransfersWithoutExactlyTwoEntries();
}