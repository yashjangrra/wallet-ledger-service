package com.yash.ledgerservice.config;

import com.yash.ledgerservice.model.Account;
import com.yash.ledgerservice.repository.AccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FundingAccountInitializer implements ApplicationRunner {

    private static final List<String> SUPPORTED_CURRENCIES = List.of("INR");

    private final AccountRepository accountRepository;

    public FundingAccountInitializer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String currency : SUPPORTED_CURRENCIES) {
            boolean exists = accountRepository
                    .findFirstByOwnerNameAndCurrency(SystemAccounts.FUNDING_OWNER, currency)
                    .isPresent();
            if (!exists) {
                accountRepository.save(new Account(SystemAccounts.FUNDING_OWNER, currency));
            }
        }
    }
}