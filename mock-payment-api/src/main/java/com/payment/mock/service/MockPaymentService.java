package com.payment.mock.service;

import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionErrorCode;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.repository.TransactionRepository;
import com.payment.mock.service.TransactionLookupService.TransactionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MockPaymentService {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentService.class);

    private final TransactionRepository transactionRepository;
    private final FailureSimulator failureSimulator;
    private final DelaySimulator delaySimulator;

    public MockPaymentService(TransactionRepository transactionRepository,
                             FailureSimulator failureSimulator,
                             DelaySimulator delaySimulator) {
        this.transactionRepository = transactionRepository;
        this.failureSimulator = failureSimulator;
        this.delaySimulator = delaySimulator;
    }

    @Transactional
    public Transaction processPayment(String transactionId, BigDecimal amount, String currency, String clientReference) {
        log.info("Processing payment: {} for amount {} {}", transactionId, amount, currency);

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("TransactionId is required");
        }

        if (transactionRepository.existsByTransactionId(transactionId)) {
            log.warn("Transaction {} already exists", transactionId);
            return transactionRepository.findByTransactionId(transactionId).orElseThrow();
        }

        long startTimeMs = System.currentTimeMillis();

        Transaction transaction = new Transaction(transactionId, amount, currency, clientReference);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction = transactionRepository.save(transaction);

        long delayMs = delaySimulator.calculateRandomDelay();
        delaySimulator.applyDelay(delayMs);

        if (failureSimulator.shouldFail()) {
            TransactionErrorCode errorCode = failureSimulator.generateFailureScenario();
            if (errorCode == null) {
                log.warn("Failure simulator returned null; defaulting to SERVICE_UNAVAILABLE");
                errorCode = TransactionErrorCode.SERVICE_UNAVAILABLE;
            }
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureCode(errorCode.getCode());
            transaction.setFailureReason(errorCode.name() + " - simulated failure");
            transaction.setProcessedAt(LocalDateTime.now());
            transaction.setResponseTimeMs(System.currentTimeMillis() - startTimeMs);
            transaction = transactionRepository.save(transaction);
            log.warn("Payment {} failed: {}", transactionId, transaction.getFailureReason());
            return transaction;
        }

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setResponseTimeMs(System.currentTimeMillis() - startTimeMs);
        transaction = transactionRepository.save(transaction);
        log.info("Payment {} completed successfully", transactionId);

        return transaction;
    }

    public Transaction getTransaction(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
    }
}
