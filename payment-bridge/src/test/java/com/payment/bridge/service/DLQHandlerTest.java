package com.payment.bridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.bridge.model.DeadLetterQueueEntry;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DLQHandler entry creation with failure context.
 */
@SpringBootTest
@ActiveProfiles("test")
class DLQHandlerTest {

    @Autowired
    private DLQHandler dlqHandler;

    @Autowired
    private DeadLetterQueueRepository dlqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateDLQEntry_WithFullContext() {
        // Given: A failed payment and task
        Payment payment = createTestPayment();
        MessageQueueTask task = createTestTask();
        String failedAction = "EXTERNAL_API_CALL";
        String failureReason = "Connection timeout";
        String apiResponse = "{\"error\":\"timeout\",\"code\":504}";

        // When: Create DLQ entry
        DeadLetterQueueEntry entry = dlqHandler.createDLQEntry(payment, task, failedAction, failureReason, apiResponse);

        // Then: Entry is created with correct data
        assertThat(entry.getDlqId()).isNotNull();
        assertThat(entry.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(entry.getFailedAction()).isEqualTo(failedAction);
        assertThat(entry.getFailureReason()).isEqualTo(failureReason);
        assertThat(entry.getApiResponse()).isEqualTo(apiResponse);
        assertThat(entry.getCreatedAt()).isNotNull();

        // Verify payment context JSON
        assertThat(entry.getPaymentContext()).isNotNull();
        // Could parse and verify JSON content if needed

        // Verify retry history JSON
        assertThat(entry.getRetryHistory()).isNotNull();
        // Could parse and verify JSON content if needed

        // Verify entry was saved
        DeadLetterQueueEntry saved = dlqRepository.findById(entry.getDlqId()).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getPaymentId()).isEqualTo(payment.getPaymentId());
    }

    @Test
    void testCreateDBFailureDLQEntry() {
        // Given: A payment with DB failure
        Payment payment = createTestPayment();
        MessageQueueTask task = createTestTask();
        String operation = "status_update";
        Exception exception = new RuntimeException("Optimistic lock exception");

        // When: Create DB failure DLQ entry
        DeadLetterQueueEntry entry = dlqHandler.createDBFailureDLQEntry(payment, task, operation, exception);

        // Then: Entry has correct DB failure details
        assertThat(entry.getFailedAction()).isEqualTo("DB_STATUS_UPDATE");
        assertThat(entry.getFailureReason()).isEqualTo("Optimistic lock exception");
        assertThat(entry.getApiResponse()).isNull();
        assertThat(entry.getPaymentId()).isEqualTo(payment.getPaymentId());
    }

    @Test
    void testCreateAPIFailureDLQEntry() {
        // Given: A payment with API failure
        Payment payment = createTestPayment();
        MessageQueueTask task = createTestTask();
        String apiResponse = "{\"error\":\"server_error\",\"status\":500}";
        Exception exception = new RuntimeException("HTTP 500 Internal Server Error");

        // When: Create API failure DLQ entry
        DeadLetterQueueEntry entry = dlqHandler.createAPIFailureDLQEntry(payment, task, apiResponse, exception);

        // Then: Entry has correct API failure details
        assertThat(entry.getFailedAction()).isEqualTo("EXTERNAL_API_CALL");
        assertThat(entry.getFailureReason()).isEqualTo("HTTP 500 Internal Server Error");
        assertThat(entry.getApiResponse()).isEqualTo(apiResponse);
        assertThat(entry.getPaymentId()).isEqualTo(payment.getPaymentId());
    }

    @Test
    void testDLQEntryContainsPaymentContext() throws Exception {
        // Given: A payment with specific details
        Payment payment = createTestPayment();
        payment.setClientReference("test-ref-123");
        payment.setAmount(new BigDecimal("99.99"));
        payment.setCurrency("EUR");

        MessageQueueTask task = createTestTask();
        task.setRetryAttempt(3);

        // When: Create DLQ entry
        DeadLetterQueueEntry entry = dlqHandler.createDLQEntry(payment, task, "TEST", "test failure", null);

        // Then: Payment context contains expected data
        String paymentContextJson = entry.getPaymentContext();
        @SuppressWarnings("unchecked")
        Map<String, Object> context = objectMapper.readValue(paymentContextJson, Map.class);

        assertThat(context.get("paymentId")).isEqualTo(payment.getPaymentId().toString());
        assertThat(context.get("amount")).isEqualTo(99.99);
        assertThat(context.get("currency")).isEqualTo("EUR");
        assertThat(context.get("clientReference")).isEqualTo("test-ref-123");
        assertThat(context.get("status")).isEqualTo("RECEIVED");
    }

    @Test
    void testDLQEntryContainsRetryHistory() throws Exception {
        // Given: A task with multiple retry attempts
        Payment payment = createTestPayment();
        MessageQueueTask task = createTestTask();
        task.setRetryAttempt(2); // This means 2 attempts have been made

        // When: Create DLQ entry
        DeadLetterQueueEntry entry = dlqHandler.createDLQEntry(payment, task, "TEST", "test failure", null);

        // Then: Retry history contains expected data
        String retryHistoryJson = entry.getRetryHistory();
        @SuppressWarnings("unchecked")
        Map<String, Object> history = objectMapper.readValue(retryHistoryJson, Map.class);

        assertThat(history.get("currentAttempt")).isEqualTo(2);
        assertThat(history.get("maxAttempts")).isEqualTo(5);

        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> attempts = (java.util.List<Map<String, Object>>) history.get("attempts");
        assertThat(attempts).hasSize(2); // 2 attempts recorded

        for (int i = 0; i < attempts.size(); i++) {
            Map<String, Object> attempt = attempts.get(i);
            assertThat(attempt.get("attemptNumber")).isEqualTo(i + 1);
            assertThat(attempt.get("action")).isEqualTo("TEST");
            assertThat(attempt.get("timestamp")).isNotNull();
        }
    }

    private Payment createTestPayment() {
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        return payment;
    }

    private MessageQueueTask createTestTask() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());
        task.setRetryAttempt(1);
        return task;
    }
}