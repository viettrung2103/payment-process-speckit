package com.payment.bridge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.bridge.model.DeadLetterQueueEntry;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DLQHandler {

    private static final Logger logger = LoggerFactory.getLogger(DLQHandler.class);

    private final DeadLetterQueueRepository dlqRepository;
    private final ObjectMapper objectMapper;

    public DLQHandler(DeadLetterQueueRepository dlqRepository, ObjectMapper objectMapper) {
        this.dlqRepository = dlqRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a DLQ entry for a failed payment with full context.
     *
     * @param payment The payment that failed
     * @param task The message queue task that failed
     * @param failedAction The action that failed (e.g., "EXTERNAL_API_CALL", "DB_UPDATE")
     * @param failureReason The reason for failure
     * @param apiResponse The API response (if applicable)
     * @return The created DLQ entry
     */
    public DeadLetterQueueEntry createDLQEntry(Payment payment, MessageQueueTask task,
                                               String failedAction, String failureReason,
                                               String apiResponse) {
        try {
            DeadLetterQueueEntry entry = new DeadLetterQueueEntry();
            entry.setDlqId(UUID.randomUUID());
            entry.setPaymentId(payment.getPaymentId());
            entry.setFailedAction(failedAction);
            entry.setFailureReason(failureReason);
            entry.setApiResponse(apiResponse);

            // Create payment context as JSON
            Map<String, Object> paymentContext = new HashMap<>();
            paymentContext.put("paymentId", payment.getPaymentId());
            paymentContext.put("amount", payment.getAmount());
            paymentContext.put("currency", payment.getCurrency());
            paymentContext.put("clientReference", payment.getClientReference());
            paymentContext.put("status", payment.getStatus());
            paymentContext.put("createdAt", payment.getCreatedAt());
            paymentContext.put("updatedAt", payment.getUpdatedAt());

            entry.setPaymentContext(objectMapper.writeValueAsString(paymentContext));

            // Create retry history as JSON
            Map<String, Object> retryHistory = new HashMap<>();
            retryHistory.put("currentAttempt", task.getRetryAttempt());
            retryHistory.put("maxAttempts", 5);

            // In a real implementation, we'd track all retry attempts
            List<Map<String, Object>> attempts = new ArrayList<>();
            for (int i = 1; i <= task.getRetryAttempt(); i++) {
                Map<String, Object> attempt = new HashMap<>();
                attempt.put("attemptNumber", i);
                attempt.put("timestamp", System.currentTimeMillis());
                attempt.put("action", failedAction);
                attempts.add(attempt);
            }
            retryHistory.put("attempts", attempts);

            entry.setRetryHistory(objectMapper.writeValueAsString(retryHistory));

            DeadLetterQueueEntry savedEntry = dlqRepository.save(entry);

            logger.error("Created DLQ entry {} for payment {} - action: {}, reason: {}",
                        savedEntry.getDlqId(), payment.getPaymentId(), failedAction, failureReason);

            return savedEntry;

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize DLQ entry data for payment {}", payment.getPaymentId(), e);
            throw new RuntimeException("Failed to create DLQ entry", e);
        }
    }

    /**
     * Creates a DLQ entry for database operation failures.
     *
     * @param payment The payment that failed to update
     * @param task The message queue task
     * @param operation The DB operation that failed
     * @param exception The exception that occurred
     * @return The created DLQ entry
     */
    public DeadLetterQueueEntry createDBFailureDLQEntry(Payment payment, MessageQueueTask task,
                                                        String operation, Exception exception) {
        return createDLQEntry(payment, task, "DB_" + operation.toUpperCase(),
                            exception.getMessage(), null);
    }

    /**
     * Creates a DLQ entry for external API failures.
     *
     * @param payment The payment that failed
     * @param task The message queue task
     * @param apiResponse The API response/error details
     * @param exception The exception that occurred
     * @return The created DLQ entry
     */
    public DeadLetterQueueEntry createAPIFailureDLQEntry(Payment payment, MessageQueueTask task,
                                                         String apiResponse, Exception exception) {
        return createDLQEntry(payment, task, "EXTERNAL_API_CALL",
                            exception.getMessage(), apiResponse);
    }
}