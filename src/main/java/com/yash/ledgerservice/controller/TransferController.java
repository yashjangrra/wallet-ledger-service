package com.yash.ledgerservice.controller;

import com.yash.ledgerservice.dto.TransferRequest;
import com.yash.ledgerservice.dto.TransferResponse;
import com.yash.ledgerservice.dto.TransferResult;
import com.yash.ledgerservice.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        TransferResult result;
        try {
            result = transferService.transfer(idempotencyKey, request);
        } catch (DataIntegrityViolationException e) {
            // Two requests with the same key raced and the other one committed first.
            // Our transaction is rolled back (and is unusable), so recover in a fresh one.
            result = transferService.findReplay(idempotencyKey, request).orElseThrow(() -> e);
        }

        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK)
                    .header("Idempotent-Replayed", "true")
                    .body(result.response());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
    }
}