package com.yash.ledgerservice.repository;

import com.yash.ledgerservice.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("""
            select coalesce(sum(
                case when e.type = com.yash.ledgerservice.model.EntryType.CREDIT
                     then e.amount else -e.amount end), 0L)
            from LedgerEntry e
            where e.account.id = :accountId
            """)
    long computeBalance(@Param("accountId") Long accountId);

    // Balance from all entries of this account created before the given entry (opening balance of a page).
    @Query("""
            select coalesce(sum(
                case when e.type = com.yash.ledgerservice.model.EntryType.CREDIT
                     then e.amount else -e.amount end), 0L)
            from LedgerEntry e
            where e.account.id = :accountId and e.id < :beforeEntryId
            """)
    long computeBalanceBefore(@Param("accountId") Long accountId,
                              @Param("beforeEntryId") Long beforeEntryId);

    Page<LedgerEntry> findByAccountId(Long accountId, Pageable pageable);

    // Reconciliation check 1: credits minus debits across the WHOLE ledger. Must be 0.
    @Query("""
            select coalesce(sum(
                case when e.type = com.yash.ledgerservice.model.EntryType.CREDIT
                     then e.amount else -e.amount end), 0L)
            from LedgerEntry e
            """)
    long netSumOfAllEntries();

    // Reconciliation check 2: journals whose credits and debits do not cancel out.
    @Query("""
            select e.journalId from LedgerEntry e
            group by e.journalId
            having sum(case when e.type = com.yash.ledgerservice.model.EntryType.CREDIT
                            then e.amount else -e.amount end) <> 0L
            """)
    List<UUID> findUnbalancedJournals(Pageable pageable);
}