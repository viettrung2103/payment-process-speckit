package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.exception.PaymentApiException;
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
 * Integration tests for API 4xx error immediate DLQ handling.
 * Verifies that client errors (4xx) are sent directly to DLQ without retries.
 */
@SpringBootTest
@ActiveProfiles("test")
class APIErrorClassificationTest {

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
    void testAPI4xxErrorSendsToDLQImmediately() throws IOException {
        // Given: A payment that will fail with 4xx client error
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock external API to throw 4xx error (non-retryable)
        PaymentApiException apiException = new PaymentApiException("Bad Request", 400, "{\"error\":\"invalid_amount\"}");
        doThrow(apiException)
            .when(externalApiClient).processPayment(any(Payment.class));

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task and API returns 4xx error
        paymentWorker.receive(task, message, channel);

        // Then: Message should be NACKed (since processing failed)
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());

        // And: No retry should be scheduled (4xx errors don't get retried)
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());

        // And: Payment should be marked as FAILED
        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment).isPresent();
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failedPayment.get().getErrorReason()).isEqualTo("Bad Request");

        // And: DLQ entry should be created
        long dlqCount = dlqRepository.findByPaymentId(paymentId).size();
        assertThat(dlqCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void testAPI401ErrorSendsToDLQImmediately() throws IOException {
        // Given: A payment that will fail with 401 Unauthorized
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock external API to throw 401 error
        PaymentApiException apiException = new PaymentApiException("Unauthorized", 401, "{\"error\":\"invalid_credentials\"}");
        doThrow(apiException)
            .when(externalApiClient).processPayment(any(Payment.class));

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task
        paymentWorker.receive(task, message, channel);

        // Then: No retry scheduled, payment failed, DLQ entry created
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));

        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);

        long dlqCount = dlqRepository.findByPaymentId(paymentId).size();
        assertThat(dlqCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void testAPI403ErrorSendsToDLQImmediately() throws IOException {
        // Given: A payment that will fail with 403 Forbidden
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock external API to throw 403 error
        PaymentApiException apiException = new PaymentApiException("Forbidden", 403, "{\"error\":\"insufficient_permissions\"}");
        doThrow(apiException)
            .when(externalApiClient).processPayment(any(Payment.class));

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task
        paymentWorker.receive(task, message, channel);

        // Then: No retry scheduled, payment failed, DLQ entry created
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));

        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);

        long dlqCount = dlqRepository.findByPaymentId(paymentId).size();
        assertThat(dlqCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void testAPI422ErrorSendsToDLQImmediately() throws IOException {
        // Given: A payment that will fail with 422 Unprocessable Entity
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock external API to throw 422 error
        PaymentApiException apiException = new PaymentApiException("Unprocessable Entity", 422, "{\"error\":\"validation_failed\"}");
        doThrow(apiException)
            .when(externalApiClient).processPayment(any(Payment.class));

        // Mock message and channel
        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        // When: Worker processes the task
        paymentWorker.receive(task, message, channel);

        // Then: No retry scheduled, payment failed, DLQ entry created
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());
        verify(channel).basicNack(anyLong(), anyBoolean(), eq(false));

        Optional<Payment> failedPayment = paymentRepository.findById(paymentId);
        assertThat(failedPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);

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