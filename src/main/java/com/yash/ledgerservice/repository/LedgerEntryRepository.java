package com.yash.ledgerservice.repository;

import com.yash.ledgerservice.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("""
            select coalesce(sum(
                case when e.type = com.yash.ledgerservice.model.EntryType.CREDIT
                     then e.amount else -e.amount end), 0L)
            from LedgerEntry e
            where e.account.id = :accountId
            """)
    long computeBalance(@Param("accountId") Long accountId);
}