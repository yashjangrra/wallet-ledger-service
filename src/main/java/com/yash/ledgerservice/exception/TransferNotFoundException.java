package com.yash.ledgerservice.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer #" + id + " not found");
    }
}
