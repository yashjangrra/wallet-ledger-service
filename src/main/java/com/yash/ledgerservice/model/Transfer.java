package com.yash.ledgerservice.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nullable on purpose: transfers created before Task 5 have no key.
    // PostgreSQL allows many NULLs in a UNIQUE column.
    @Column(name = "idempotency_key", unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "journal_id", nullable = false, updatable = false, unique = true)
    private UUID journalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false, updatable = false)
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false, updatable = false)
    private Account toAccount;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
        // required by JPA
    }

    public Transfer(String idempotencyKey, UUID journalId, Account fromAccount,
                    Account toAccount, long amount, String currency) {
        this.idempotencyKey = idempotencyKey;
        this.journalId = journalId;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferStatus.COMPLETED;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getJournalId() { return journalId; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public TransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}