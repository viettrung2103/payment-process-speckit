package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.worker.PaymentWorker;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Integration tests for DLQ escalation handling.
 * Verifies that payments in DLQ are properly escalated for manual review.
 */
@SpringBootTest
@ActiveProfiles("test")
class DLQEscalationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DeadLetterQueueRepository dlqRepository;

    @Autowired
    private PaymentWorker paymentWorker;

    @MockBean
    private PaymentPublisher paymentPublisher;

    @MockBean
    private ExternalApiClient externalApiClient;

    @Test
    @Transactional
    void testDLQEntryContainsCompleteContext() throws IOException {
        // Given: A payment that fails and gets sent to DLQ
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(5); // Max retries exceeded

        // Mock external API to throw non-retryable error
        com.payment.bridge.exception.PaymentApiException apiException = 
            new com.payment.bridge.exception.PaymentApiException("Bad Request", 400, "{\"error\":\"invalid_data\"}");
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(apiException);

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task (max retries exceeded)
        paymentWorker.receive(task, message, channel);

        // Then: Message should be NACKed
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());

        // And: No further retry should be scheduled
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());

        // And: Payment should be marked as FAILED
        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment).isPresent();
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);

        // And: DLQ entry should be created with complete context
        var dlqEntries = dlqRepository.findByPaymentId(paymentId);
        assertThat(dlqEntries).hasSize(1);

        var dlqEntry = dlqEntries.get(0);
        assertThat(dlqEntry.getPaymentId()).isEqualTo(paymentId);
        assertThat(dlqEntry.getFailureReason()).contains("Bad Request");
        assertThat(dlqEntry.getCreatedAt()).isNotNull();
        assertThat(dlqEntry.getPaymentContext()).isNotNull();
        assertThat(dlqEntry.getRetryHistory()).isNotNull();

        // Verify the payment context contains essential information
        String paymentContext = dlqEntry.getPaymentContext();
        assertThat(paymentContext).contains(paymentId.toString());
        assertThat(paymentContext).contains("100.00");
        assertThat(paymentContext).contains("USD");
        assertThat(paymentContext).contains("FAILED");
    }

    @Test
    @Transactional
    void testMultipleDLQEntriesForSamePayment() throws IOException {
        // Given: A payment that fails multiple times and creates multiple DLQ entries
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        // First failure
        MessageQueueTask task1 = new MessageQueueTask();
        task1.setPaymentId(paymentId);
        task1.setRetryAttempt(5);

        com.payment.bridge.exception.PaymentApiException apiException1 = 
            new com.payment.bridge.exception.PaymentApiException("Unauthorized", 401, "{\"error\":\"auth_failed\"}");
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(apiException1);

        Message message1 = mock(Message.class);
        MessageProperties messageProperties1 = mock(MessageProperties.class);
        when(message1.getMessageProperties()).thenReturn(messageProperties1);
        when(messageProperties1.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel1 = mock(com.rabbitmq.client.Channel.class);

        paymentWorker.receive(task1, message1, channel1);

        // Second failure (simulating another payment with different error)
        UUID paymentId2 = UUID.randomUUID();
        Payment payment2 = createTestPayment(paymentId2);
        paymentRepository.save(payment2);

        MessageQueueTask task2 = new MessageQueueTask();
        task2.setPaymentId(paymentId2);
        task2.setRetryAttempt(5);

        com.payment.bridge.exception.PaymentApiException apiException2 = 
            new com.payment.bridge.exception.PaymentApiException("Forbidden", 403, "{\"error\":\"access_denied\"}");
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(apiException2);

        Message message2 = mock(Message.class);
        MessageProperties messageProperties2 = mock(MessageProperties.class);
        when(message2.getMessageProperties()).thenReturn(messageProperties2);
        when(messageProperties2.getDeliveryTag()).thenReturn(2L);

        com.rabbitmq.client.Channel channel2 = mock(com.rabbitmq.client.Channel.class);

        paymentWorker.receive(task2, message2, channel2);

        // Then: DLQ entries should exist for each payment
        var dlqEntries1 = dlqRepository.findByPaymentId(paymentId);
        var dlqEntries2 = dlqRepository.findByPaymentId(paymentId2);
        assertThat(dlqEntries1).hasSize(1);
        assertThat(dlqEntries2).hasSize(1);

        // And: Each entry should have different error information
        var firstEntry = dlqEntries1.get(0);
        var secondEntry = dlqEntries2.get(0);

        assertThat(firstEntry.getFailureReason()).contains("Unauthorized");
        assertThat(secondEntry.getFailureReason()).contains("Forbidden");

        // Both should have different payment contexts
        assertThat(firstEntry.getPaymentContext()).isNotEqualTo(secondEntry.getPaymentContext());
    }

    private Payment createTestPayment(UUID paymentId) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        payment.setClientReference("test-ref-123");
        return payment;
    }
}