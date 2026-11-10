package com.yash.ledgerservice.dto;

public record TransferResult(
        TransferResponse response,
        boolean replayed
) {}