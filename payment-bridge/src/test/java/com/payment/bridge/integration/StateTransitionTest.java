package com.payment.bridge.integration;

import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.exception.PaymentApiException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import com.payment.bridge.repository.PaymentAuditRepository;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.service.PaymentService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class StateTransitionTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAuditRepository paymentAuditRepository;

    @Autowired
    private PaymentWorker paymentWorker;

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private ExternalApiClient externalApiClient;

    @MockBean
    private DeadLetterQueueRepository deadLetterQueueRepository;

    @Test
    @Transactional
    void testSuccessfulStateTransitionCreatesAuditEntries() throws IOException {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        com.payment.bridge.client.ExternalApiClient.ApiResponse successResponse = 
            new com.payment.bridge.client.ExternalApiClient.ApiResponse();
        successResponse.setTransactionId(paymentId.toString());
        successResponse.setStatus("SUCCESS");
        successResponse.setStatusCode(200);
        successResponse.setBody("{\"transactionId\":\"" + paymentId + "\",\"status\":\"SUCCESS\"}");

        when(externalApiClient.processPayment(any(Payment.class))).thenReturn(successResponse);

        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setAction("PROCESS_PAYMENT");
        task.setRetryAttempt(0);

        paymentWorker.receive(task, message, channel);

        Payment completedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(completedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        var auditEntries = paymentAuditRepository.findByPaymentIdOrderByChangedAtDesc(paymentId);
        assertThat(auditEntries).hasSizeGreaterThanOrEqualTo(1);
        assertThat(auditEntries.get(0).getNewStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Transactional
    void testFailedStateTransitionCreatesFailedAuditEntry() throws IOException {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createTestPayment(paymentId);
        paymentRepository.save(payment);

        when(deadLetterQueueRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentApiException apiException = new PaymentApiException("Bad Request", 400, "{\"error\":\"invalid_data\"}");
        when(externalApiClient.processPayment(any(Payment.class))).thenThrow(apiException);

        Message message = mock(Message.class);
        MessageProperties messageProperties = mock(MessageProperties.class);
        when(message.getMessageProperties()).thenReturn(messageProperties);
        when(messageProperties.getDeliveryTag()).thenReturn(1L);

        com.rabbitmq.client.Channel channel = mock(com.rabbitmq.client.Channel.class);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setAction("PROCESS_PAYMENT");
        task.setRetryAttempt(5);

        paymentWorker.receive(task, message, channel);

        Payment failedPayment = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(failedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        var auditEntries = paymentAuditRepository.findByPaymentIdOrderByChangedAtDesc(paymentId);
        assertThat(auditEntries).hasSizeGreaterThanOrEqualTo(2);
        assertThat(auditEntries.get(0).getNewStatus()).isEqualTo("FAILED");
        assertThat(auditEntries.get(1).getNewStatus()).isEqualTo("IN_PROGRESS");
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
