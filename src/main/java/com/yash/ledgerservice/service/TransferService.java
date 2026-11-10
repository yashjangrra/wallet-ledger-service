package com.yash.ledgerservice.service;

import com.yash.ledgerservice.config.SystemAccounts;
import com.yash.ledgerservice.dto.TransferRequest;
import com.yash.ledgerservice.dto.TransferResponse;
import com.yash.ledgerservice.dto.TransferResult;
import com.yash.ledgerservice.exception.AccountNotFoundException;
import com.yash.ledgerservice.exception.BusinessRuleException;
import com.yash.ledgerservice.exception.IdempotencyConflictException;
import com.yash.ledgerservice.exception.InsufficientFundsException;
import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.model.EntryType;
import com.yash.ledgerservice.model.LedgerEntry;
import com.yash.ledgerservice.model.Transfer;
import com.yash.ledgerservice.repository.AccountRepository;
import com.yash.ledgerservice.repository.LedgerEntryRepository;
import com.yash.ledgerservice.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferRepository transferRepository;

    public TransferService(AccountRepository accountRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public TransferResult transfer(String idempotencyKey, TransferRequest request) {

        // 1. Have we already processed this key? If yes, replay: do NOT move money again.
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        // 2. First time we see this key: validate and execute.
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }

        Account from = accountRepository.findById(request.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.fromAccountId()));
        Account to = accountRepository.findById(request.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.toAccountId()));

        if (isSystemAccount(from) || isSystemAccount(to)) {
            throw new BusinessRuleException("System accounts cannot be used in transfers");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Currency mismatch: " + from.getCurrency()
                    + " vs " + to.getCurrency());
        }

        long amount = request.amount();

        // CHECK the balance...
        long balance = ledgerEntryRepository.computeBalance(from.getId());
        if (balance < amount) {
            throw new InsufficientFundsException(from.getId(), balance, amount);
        }

        // ...then ACT. The gap between these two is a race condition (fixed in Task 6).
        UUID journalId = UUID.randomUUID();
        Transfer transfer = transferRepository.save(
                new Transfer(idempotencyKey, journalId, from, to, amount, from.getCurrency()));
        ledgerEntryRepository.save(new LedgerEntry(journalId, from, EntryType.DEBIT, amount));
        ledgerEntryRepository.save(new LedgerEntry(journalId, to, EntryType.CREDIT, amount));

        return new TransferResult(TransferResponse.from(transfer), false);
    }

    /**
     * Used by the controller after a unique-constraint race: another request with the
     * same key committed first, so we look up its result in a fresh transaction.
     */
    @Transactional(readOnly = true)
    public Optional<TransferResult> findReplay(String idempotencyKey, TransferRequest request) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, request));
    }

    private TransferResult replay(Transfer existing, TransferRequest request) {
        boolean sameRequest =
                existing.getFromAccount().getId().equals(request.fromAccountId())
                        && existing.getToAccount().getId().equals(request.toAccountId())
                        && existing.getAmount() == request.amount();

        if (!sameRequest) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        return new TransferResult(TransferResponse.from(existing), true);
    }

    private boolean isSystemAccount(Account account) {
        return SystemAccounts.FUNDING_OWNER.equals(account.getOwnerName());
    }
}