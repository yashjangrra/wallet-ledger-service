package com.yash.ledgerservice.exception;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(Long accountId, long balance, long requested) {
        super("Insufficient funds in account " + accountId
                + ": balance " + balance + ", requested " + requested);
    }
}