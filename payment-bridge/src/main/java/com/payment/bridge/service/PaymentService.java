package com.payment.bridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.bridge.amqp.PaymentTaskPublisher;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.metrics.LatencyMetrics;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.repository.PaymentRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentTaskPublisher paymentPublisher;
    private final ExternalApiClient externalApiClient;
    private final PaymentAuditService paymentAuditService;
    private final Executor taskExecutor;
    private final LatencyMetrics latencyMetrics;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          IdempotencyService idempotencyService,
                          PaymentTaskPublisher paymentPublisher,
                          ExternalApiClient externalApiClient,
                          PaymentAuditService paymentAuditService,
                          Executor taskExecutor,
                          LatencyMetrics latencyMetrics,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.paymentPublisher = paymentPublisher;
        this.externalApiClient = externalApiClient;
        this.paymentAuditService = paymentAuditService;
        this.taskExecutor = taskExecutor;
        this.latencyMetrics = latencyMetrics;
        this.objectMapper = objectMapper;
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        return createPayment(request, null);
    }

    private static final long SLOW_OPERATION_THRESHOLD_MS = 100;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKeyHeader) {
        Timer.Sample sample = latencyMetrics.startIngestionTimer();
        try {
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

                paymentAuditService.recordTransition(savedPayment, null, savedPayment.getStatus(), "Payment created", "gateway");

                // Publish to message queue for processing asynchronously to keep ingestion latency low
                publishPaymentTask(savedPayment);

                return mapToResponse(savedPayment);
            } catch (Exception e) {
                logger.error("Failed to create payment", e);
                throw new PaymentProcessingException("Failed to create payment", e);
            }
        } finally {
            Duration elapsed = latencyMetrics.recordIngestionLatency(sample);
            if (elapsed.toMillis() > SLOW_OPERATION_THRESHOLD_MS) {
                logger.error("[CRITICAL] Slow ingestion operation detected: {} ms for payment request", elapsed.toMillis());
            }
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

    /**
     * Publish payment processing tasks only after the current transaction commits.
     *
     * The worker consumes messages immediately, so publishing a task before the
     * transaction completes can result in a worker receiving a task for a payment
     * row that is not yet committed and therefore not visible in the database.
     */
    private void publishPaymentTask(Payment payment) {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(payment.getPaymentId());
        task.setAction("PROCESS_PAYMENT");
        task.setRetryAttempt(0);
        task.setEnqueuedAt(Instant.now());
        task.setDequeueCount(0);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logger.info("Transaction committed; publishing payment task after commit: {}", task.getPaymentId());
                    executePublishTask(task);
                }

                @Override
                public void beforeCommit(boolean readOnly) {
                }

                @Override
                public void afterCompletion(int status) {
                }

                @Override
                public void beforeCompletion() {
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }

                @Override
                public void flush() {
                }
            });
        } else {
            logger.warn("Transaction synchronization is not active; publishing payment task immediately: {}", task.getPaymentId());
            executePublishTask(task);
        }
    }

    private void executePublishTask(MessageQueueTask task) {
        try {
            taskExecutor.execute(() -> {
                try {
                    paymentPublisher.publishPaymentTask(task);
                    logger.debug("Published payment task for processing: {}", task.getPaymentId());
                } catch (Exception e) {
                    logger.error("Failed to publish payment task asynchronously: {}", task.getPaymentId(), e);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to submit payment publish task for execution: {}", task.getPaymentId(), e);
        }
    }

    @Transactional
    public void processPaymentWithExternalAPI(UUID paymentId) {
        Timer.Sample sample = latencyMetrics.startProcessingTimer();
        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new PaymentProcessingException("Payment not found: " + paymentId));

            PaymentStatus originalStatus = payment.getStatus();
            if (originalStatus != PaymentStatus.IN_PROGRESS) {
                payment.setStatus(PaymentStatus.IN_PROGRESS);
                paymentRepository.save(payment);
                paymentAuditService.recordTransition(payment, originalStatus, PaymentStatus.IN_PROGRESS,
                        "Processing started", "worker");
            }

            var apiResponse = externalApiClient.processPayment(payment);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExternalTransactionId(apiResponse.getTransactionId());
            payment.setApiStatusCode(apiResponse.getStatusCode());
            try {
                payment.setApiResponse(objectMapper.writeValueAsString(apiResponse));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize API response for payment {}, storing plain string", paymentId, e);
                payment.setApiResponse(apiResponse.toString());
            }
            paymentRepository.save(payment);
            paymentAuditService.recordTransition(payment, PaymentStatus.IN_PROGRESS, PaymentStatus.COMPLETED,
                    "Processing completed", "worker");
        } catch (Exception e) {
            logger.error("Payment {} failed during external API processing", paymentId, e);
            throw e;
        } finally {
            Duration elapsed = latencyMetrics.recordProcessingLatency(sample);
            if (elapsed.toMillis() > SLOW_OPERATION_THRESHOLD_MS) {
                logger.error("[CRITICAL] Slow processing operation detected: {} ms for payment {}", elapsed.toMillis(), paymentId);
            }
        }
    }

    private boolean hasIdempotencyHeader(String idempotencyKeyHeader) {
        return idempotencyKeyHeader != null && !idempotencyKeyHeader.trim().isEmpty();
    }
}