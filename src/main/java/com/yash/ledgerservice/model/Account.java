package com.yash.ledgerservice.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
        // required by JPA
    }

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOwnerName() { return ownerName; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}