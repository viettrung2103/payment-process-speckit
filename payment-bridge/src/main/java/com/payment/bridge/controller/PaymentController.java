package com.payment.bridge.controller;

import com.payment.bridge.exception.IdempotencyViolationException;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final Executor taskExecutor;

    public PaymentController(PaymentService paymentService,
                             @Qualifier("virtualThreadExecutor") Executor taskExecutor) {
        this.paymentService = paymentService;
        this.taskExecutor = taskExecutor;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<PaymentResponse>> createPayment(
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody PaymentRequest request) {

        logger.info("Received payment request for amount: {} {}, idempotencyKey: {}",
                   request.getAmount(), request.getCurrency(), idempotencyKey);

        return CompletableFuture.supplyAsync(() -> {
            try {
                PaymentResponse response = paymentService.createPayment(request, idempotencyKey);

                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(URI.create("/api/v1/payments/status/" + response.getPaymentId()));
                headers.set("X-Payment-ID", response.getPaymentId().toString());

                Map<String, Map<String, String>> links = new HashMap<>();
                Map<String, String> selfLink = new HashMap<>();
                selfLink.put("href", "/api/v1/payments/status/" + response.getPaymentId());
                links.put("self", selfLink);
                links.put("status", selfLink);
                response.setLinks(links);

                logger.info("Payment request processed successfully: {}", response.getPaymentId());
                return ResponseEntity.accepted().headers(headers).body(response);

            } catch (IdempotencyViolationException e) {
                logger.warn("Idempotency violation: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new PaymentResponse(null, "DUPLICATE_REQUEST", e.getMessage()));

            } catch (IllegalArgumentException e) {
                logger.warn("Validation error: {}", e.getMessage());
                return ResponseEntity.badRequest()
                        .body(new PaymentResponse(null, "VALIDATION_ERROR", e.getMessage()));

            } catch (Exception e) {
                logger.error("Unexpected error processing payment request", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new PaymentResponse(null, "INTERNAL_ERROR", "An unexpected error occurred"));
            }
        }, taskExecutor);
    }
}