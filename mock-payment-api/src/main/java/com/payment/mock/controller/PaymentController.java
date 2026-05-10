package com.payment.mock.controller;

import com.payment.mock.dto.ErrorResponse;
import com.payment.mock.dto.PaymentRequest;
import com.payment.mock.dto.PaymentResponse;
import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionErrorCode;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.service.MockPaymentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final MockPaymentService paymentService;

    public PaymentController(MockPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping({"/pay", "/payments"})
    public ResponseEntity<?> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Received payment request: {}", request);

        try {
            Transaction transaction = paymentService.processPayment(
                    request.getTransactionId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getClientReference()
            );

            return buildResponse(transaction);
        } catch (IllegalArgumentException ex) {
            log.warn("Validation failure processing payment", ex);
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, ex.getMessage(), TransactionErrorCode.VALIDATION_ERROR.getCode(), request.getTransactionId(), null));
        } catch (Exception ex) {
            log.error("Error processing payment", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Internal server error", TransactionErrorCode.INTERNAL_ERROR.getCode(), request.getTransactionId(), null));
        }
    }

    @GetMapping("/payments/status/{transactionId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String transactionId) {
        log.info("Checking status for transaction: {}", transactionId);

        Transaction transaction = paymentService.getTransaction(transactionId);
        return buildResponse(transaction);
    }

    private ResponseEntity<?> buildResponse(Transaction transaction) {
        String message = transaction.getStatus() == TransactionStatus.FAILED
                ? "FAILED"
                : "OK";

        PaymentResponse response = new PaymentResponse(
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

        return ResponseEntity.ok(response);
    }
}
