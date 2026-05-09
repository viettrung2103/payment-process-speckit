package com.payment.mock.controller;

import com.payment.mock.dto.PaymentResponse;
import com.payment.mock.entity.Transaction;
import com.payment.mock.service.TransactionLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final TransactionLookupService transactionLookupService;

    public TransactionController(TransactionLookupService transactionLookupService) {
        this.transactionLookupService = transactionLookupService;
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<PaymentResponse> getTransaction(@PathVariable String transactionId) {
        log.info("Retrieving transaction: {}", transactionId);

        Transaction transaction = transactionLookupService.findByTransactionIdOrThrow(transactionId);
        PaymentResponse response = buildResponse(transaction);

        return ResponseEntity.ok(response);
    }

    private PaymentResponse buildResponse(Transaction transaction) {
        String message = transaction.getStatus() == null || transaction.getStatus().name().equals("FAILED")
                ? "FAILED"
                : "OK";

        return new PaymentResponse(
                200,
                message,
                transaction.getTransactionId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus() != null ? transaction.getStatus().toString() : "UNKNOWN",
                transaction.getClientReference(),
                transaction.getProcessedAt(),
                transaction.getResponseTimeMs(),
                transaction.getFailureCode(),
                transaction.getFailureReason()
        );
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getTransactionHistory(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        
        log.info("Retrieving transaction history: offset={}, limit={}", offset, limit);
        
        // Validate and sanitize limit
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (offset < 0) {
            offset = 0;
        }
        
        Pageable pageable = PageRequest.of(offset / limit, limit, 
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Transaction> page = transactionLookupService.findAll(pageable);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transactions", page.getContent());
        response.put("offset", offset);
        response.put("limit", limit);
        response.put("totalCount", page.getTotalElements());
        response.put("pageCount", page.getTotalPages());
        
        return ResponseEntity.ok(response);
    }
}
