package com.yash.ledgerservice.service;

import com.yash.ledgerservice.config.SystemAccounts;
import com.yash.ledgerservice.dto.AccountResponse;
import com.yash.ledgerservice.dto.CreateAccountRequest;
import com.yash.ledgerservice.exception.AccountNotFoundException;
import com.yash.ledgerservice.exception.BusinessRuleException;
import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        String ownerName = request.ownerName().trim();
        if (SystemAccounts.FUNDING_OWNER.equalsIgnoreCase(ownerName)) {
            throw new BusinessRuleException("ownerName '" + ownerName + "' is reserved");
        }
        Account account = new Account(ownerName, request.currency());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}