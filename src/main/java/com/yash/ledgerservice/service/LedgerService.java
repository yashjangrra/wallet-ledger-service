package com.yash.ledgerservice.service;

import com.yash.ledgerservice.config.SystemAccounts;
import com.yash.ledgerservice.dto.BalanceResponse;
import com.yash.ledgerservice.dto.DepositRequest;
import com.yash.ledgerservice.exception.AccountNotFoundException;
import com.yash.ledgerservice.exception.BusinessRuleException;
import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.model.EntryType;
import com.yash.ledgerservice.model.LedgerEntry;
import com.yash.ledgerservice.repository.AccountRepository;
import com.yash.ledgerservice.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(AccountRepository accountRepository,
                         LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public BalanceResponse deposit(Long accountId, DepositRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (SystemAccounts.FUNDING_OWNER.equals(account.getOwnerName())) {
            throw new BusinessRuleException("Cannot deposit into a system account");
        }

        Account funding = accountRepository
                .findFirstByOwnerNameAndCurrency(SystemAccounts.FUNDING_OWNER, account.getCurrency())
                .orElseThrow(() -> new BusinessRuleException(
                        "Deposits are not supported for currency " + account.getCurrency()));

        long amount = request.amount();
        UUID journalId = UUID.randomUUID();

        // Both entries are saved in ONE transaction: both succeed, or neither does.
        ledgerEntryRepository.save(new LedgerEntry(journalId, funding, EntryType.DEBIT, amount));
        ledgerEntryRepository.save(new LedgerEntry(journalId, account, EntryType.CREDIT, amount));

        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                ledgerEntryRepository.computeBalance(account.getId()));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                ledgerEntryRepository.computeBalance(account.getId()));
    }
}