package com.yash.ledgerservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries",
        indexes = @Index(name = "idx_ledger_entries_account", columnList = "account_id"))
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private UUID journalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 6)
    private EntryType type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // required by JPA
    }

    public LedgerEntry(UUID journalId, Account account, EntryType type, long amount) {
        this.journalId = journalId;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getJournalId() { return journalId; }
    public Account getAccount() { return account; }
    public EntryType getType() { return type; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}