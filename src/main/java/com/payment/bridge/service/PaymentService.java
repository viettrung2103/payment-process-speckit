package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentPublisher paymentPublisher;

    public PaymentService(PaymentRepository paymentRepository, IdempotencyService idempotencyService, PaymentPublisher paymentPublisher) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.paymentPublisher = paymentPublisher;
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        return createPayment(request, null);
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKeyHeader) {
        logger.info("Creating payment for amount: {} {}", request.getAmount(), request.getCurrency());

        // Validate request
        validatePaymentRequest(request);

        // Determine idempotency key from header or request metadata
        String idempotencyKey = hasIdempotencyHeader(idempotencyKeyHeader)
            ? idempotencyKeyHeader
            : idempotencyService.generateIdempotencyKey(
                request.getClientReference(),
                generateRequestSignature(request)
            );

        // Create payment entity
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setClientReference(request.getClientReference());
        payment.setStatus(PaymentStatus.RECEIVED);
        // createdAt and updatedAt are handled by @CreationTimestamp and @UpdateTimestamp

        // Check idempotency
        var existingPayment = idempotencyService.checkIdempotency(idempotencyKey, payment);
        if (existingPayment.isPresent()) {
            logger.info("Returning existing payment for idempotent request: {}", existingPayment.get().getPaymentId());
            return mapToResponse(existingPayment.get());
        }

        // Save new payment
        try {
            Payment savedPayment = paymentRepository.save(payment);
            logger.info("Successfully created payment: {} with status: {}", savedPayment.getPaymentId(), savedPayment.getStatus());

            // Publish to message queue for processing
            publishPaymentTask(savedPayment);

            return mapToResponse(savedPayment);
        } catch (Exception e) {
            logger.error("Failed to create payment", e);
            throw new PaymentProcessingException("Failed to create payment", e);
        }
    }

    private void validatePaymentRequest(PaymentRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
        if (!isValidCurrency(request.getCurrency())) {
            throw new IllegalArgumentException("Invalid currency code: " + request.getCurrency());
        }
    }

    private boolean isValidCurrency(String currency) {
        // Basic ISO 4217 validation - in production, use a proper currency validator
        return currency != null && currency.length() == 3 && currency.matches("[A-Z]{3}");
    }

    private String generateRequestSignature(PaymentRequest request) {
        // Simple signature based on request content
        // In production, use proper hashing with salt
        return String.format("%s:%s:%s",
            request.getAmount(),
            request.getCurrency(),
            request.getClientReference()
        );
    }

    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus().name());
        response.setMessage("Payment request accepted for processing");
        response.setEstimatedProcessingTime("PT30S");
        return response;
    }

    private void publishPaymentTask(Payment payment) {
        try {
            MessageQueueTask task = new MessageQueueTask();
            task.setPaymentId(payment.getPaymentId());
            task.setAction("PROCESS_PAYMENT");
            task.setRetryAttempt(0);
            task.setEnqueuedAt(Instant.now());
            task.setDequeueCount(0);

            paymentPublisher.publishPaymentTask(task);
            logger.debug("Published payment task for processing: {}", payment.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish payment task: {}", payment.getPaymentId(), e);
            // Note: We don't throw here because the payment is already persisted
            // The MQ publishing failure will be handled by the dead letter queue mechanism
        }
    }

    private boolean hasIdempotencyHeader(String idempotencyKeyHeader) {
        return idempotencyKeyHeader != null && !idempotencyKeyHeader.trim().isEmpty();
    }
}