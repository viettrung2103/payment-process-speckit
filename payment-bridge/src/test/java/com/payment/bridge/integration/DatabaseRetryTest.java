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
import java.net.ConnectException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Integration tests for retry handling of transient errors.
 * Verifies that network and connection errors trigger retries with exponential backoff.
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseRetryTest {

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
    void testNetworkErrorTriggersRetry() throws IOException {
        // Given: A payment that will fail with network error (retryable)
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock external API to throw network error (retryable)
        IOException ioException = new IOException("Connection refused");
        RuntimeException networkException = new RuntimeException("Network error", ioException);
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(networkException);

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task and network error occurs
        paymentWorker.receive(task, message, channel);

        // Then: Message should be NACKed due to processing failure
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());

        // And: Retry should be scheduled (network errors are retryable)
        verify(paymentPublisher).publishPaymentTaskWithDelay(any(), anyLong());

        // And: Payment status should remain IN_PROGRESS (set at start of processing)
        Optional<Payment> unchangedPayment = paymentRepository.findById(paymentId);
        assertThat(unchangedPayment).isPresent();
        assertThat(unchangedPayment.get().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        // And: No DLQ entry should be created yet
        long dlqCount = dlqRepository.findByPaymentId(paymentId).size();
        assertThat(dlqCount).isEqualTo(0);
    }

    @Test
    @Transactional
    void testNetworkErrorAfterMaxRetriesSendsToDLQ() throws IOException {
        // Given: A payment that has already been retried max times and still fails with network error
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(5); // Max retries reached

        // Mock external API to throw network error
        IOException ioException = new IOException("Connection refused");
        RuntimeException networkException = new RuntimeException("Network error", ioException);
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(networkException);

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

        // And: No further retry should be scheduled (max retries exceeded)
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());

        // And: Payment should be marked as FAILED
        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment).isPresent();
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failedPayment.get().getErrorReason()).contains("Network error");

        // And: DLQ entry should be created
        long dlqCount = dlqRepository.findByPaymentId(paymentId).size();
        assertThat(dlqCount).isEqualTo(1);
    }

    private Payment createTestPayment(UUID paymentId) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        return payment;
    }
}