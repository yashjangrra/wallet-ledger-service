package com.yash.ledgerservice.repository;

import com.yash.ledgerservice.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
}