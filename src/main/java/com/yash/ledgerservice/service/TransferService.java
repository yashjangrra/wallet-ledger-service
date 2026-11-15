package com.yash.ledgerservice.service;

import com.yash.ledgerservice.config.SystemAccounts;
import com.yash.ledgerservice.dto.TransferRequest;
import com.yash.ledgerservice.dto.TransferResponse;
import com.yash.ledgerservice.dto.TransferResult;
import com.yash.ledgerservice.exception.AccountNotFoundException;
import com.yash.ledgerservice.exception.BusinessRuleException;
import com.yash.ledgerservice.exception.IdempotencyConflictException;
import com.yash.ledgerservice.exception.InsufficientFundsException;
import com.yash.ledgerservice.exception.TransferNotFoundException;
import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.model.EntryType;
import com.yash.ledgerservice.model.LedgerEntry;
import com.yash.ledgerservice.model.Transfer;
import com.yash.ledgerservice.model.TransferStatus;
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
    public TransferResponse reverse(Long transferId) {
        Transfer original = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        if (original.getStatus() == TransferStatus.REVERSED) {
            throw new BusinessRuleException("Transfer #" + transferId + " has already been reversed");
        }

        Account originalSender = original.getFromAccount();
        Account originalReceiver = original.getToAccount();
        long amount = original.getAmount();

        // Lock accounts in ascending ID order to prevent deadlocks
        Long firstId = originalSender.getId() < originalReceiver.getId() ? originalSender.getId() : originalReceiver.getId();
        Long secondId = originalSender.getId() < originalReceiver.getId() ? originalReceiver.getId() : originalSender.getId();
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);

        Account senderToCompensate = originalSender.getId().equals(firstId) ? first : second;
        Account receiverToDeduct = originalReceiver.getId().equals(firstId) ? first : second;

        // Double-check status under lock
        Transfer lockedOriginal = transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
        if (lockedOriginal.getStatus() == TransferStatus.REVERSED) {
            throw new BusinessRuleException("Transfer #" + transferId + " has already been reversed");
        }

        // Verify receiver has sufficient funds to reverse (has not already spent the money)
        long receiverBalance = ledgerEntryRepository.computeBalance(receiverToDeduct.getId());
        if (receiverBalance < amount) {
            throw new BusinessRuleException("Cannot reverse transfer #" + transferId +
                    ": recipient account #" + receiverToDeduct.getId() +
                    " has already spent the funds (available: " + receiverBalance + ", required: " + amount + ")");
        }

        // Mark original transfer as REVERSED
        lockedOriginal.markReversed();
        transferRepository.save(lockedOriginal);

        // Post compensating double-entry journal (NEVER edits historical ledger entries)
        UUID reversalJournalId = UUID.randomUUID();
        String reversalKey = "rev-" + transferId + "-" + UUID.randomUUID().toString().substring(0, 8);

        Transfer reversalTransfer = transferRepository.save(new Transfer(
                reversalKey,
                reversalJournalId,
                receiverToDeduct,
                senderToCompensate,
                amount,
                lockedOriginal.getCurrency()
        ));

        ledgerEntryRepository.save(new LedgerEntry(reversalJournalId, receiverToDeduct, EntryType.DEBIT, amount));
        ledgerEntryRepository.save(new LedgerEntry(reversalJournalId, senderToCompensate, EntryType.CREDIT, amount));

        return TransferResponse.from(reversalTransfer);
    }

    @Transactional
    public TransferResult transfer(String idempotencyKey, TransferRequest request) {

        // 1. Fast path: key already processed -> replay, no locks needed.
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        // 2. Cheap validation before we lock anything.
        Long fromId = request.fromAccountId();
        Long toId = request.toAccountId();
        if (fromId.equals(toId)) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }

        // 3. Lock BOTH accounts in ascending ID order (deadlock avoidance).
        Long firstId = fromId < toId ? fromId : toId;
        Long secondId = fromId < toId ? toId : fromId;
        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);
        Account from = fromId.equals(firstId) ? first : second;
        Account to = fromId.equals(firstId) ? second : first;

        // 4. Re-check the key now that we hold the locks: a request with the same key
        //    may have committed while we were waiting in the queue.
        Optional<Transfer> committedWhileWaiting = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (committedWhileWaiting.isPresent()) {
            return replay(committedWhileWaiting.get(), request);
        }

        // 5. Business rules.
        if (isSystemAccount(from) || isSystemAccount(to)) {
            throw new BusinessRuleException("System accounts cannot be used in transfers");
        }
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Currency mismatch: " + from.getCurrency()
                    + " vs " + to.getCurrency());
        }

        long amount = request.amount();

        // 6. CHECK the balance. Safe now: we hold the sender's lock, so nobody else can
        //    spend from this account until we commit.
        long balance = ledgerEntryRepository.computeBalance(from.getId());
        if (balance < amount) {
            throw new InsufficientFundsException(from.getId(), balance, amount);
        }

        // 7. ACT.
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
    public java.util.List<TransferResponse> listTransfers(Long accountId) {
        java.util.List<Transfer> list = (accountId != null)
                ? transferRepository.findByFromAccountIdOrToAccountIdOrderByIdDesc(accountId, accountId)
                : transferRepository.findTop50ByOrderByIdDesc();
        return list.stream().map(TransferResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse getById(Long id) {
        return transferRepository.findById(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    public Optional<TransferResult> findReplay(String idempotencyKey, TransferRequest request) {
        return transferRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> replay(existing, request));
    }

    private Account lockAccount(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
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