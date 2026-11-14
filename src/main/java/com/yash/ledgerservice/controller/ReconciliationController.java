package com.yash.ledgerservice.controller;

import com.yash.ledgerservice.dto.ReconciliationReport;
import com.yash.ledgerservice.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    public ReconciliationReport run() {
        return reconciliationService.run();
    }
}