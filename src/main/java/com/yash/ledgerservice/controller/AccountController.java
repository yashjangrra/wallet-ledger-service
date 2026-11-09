package com.yash.ledgerservice.controller;

import com.yash.ledgerservice.dto.AccountResponse;
import com.yash.ledgerservice.dto.BalanceResponse;
import com.yash.ledgerservice.dto.CreateAccountRequest;
import com.yash.ledgerservice.dto.DepositRequest;
import com.yash.ledgerservice.service.AccountService;
import com.yash.ledgerservice.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public AccountController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse created = accountService.create(request);
        return ResponseEntity.created(URI.create("/accounts/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable("id") Long id) {
        return accountService.get(id);
    }

    @PostMapping("/{id}/deposit")
    public BalanceResponse deposit(@PathVariable("id") Long id,
                                   @Valid @RequestBody DepositRequest request) {
        return ledgerService.deposit(id, request);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable("id") Long id) {
        return ledgerService.getBalance(id);
    }
}