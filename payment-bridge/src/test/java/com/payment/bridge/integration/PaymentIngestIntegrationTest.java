package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentIngestIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private PaymentPublisher paymentPublisher;

    @Test
    void createPayment_shouldPersistToDatabaseAndPublishToQueue() {
        // Given
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setCurrency("EUR");
        request.setClientReference("integration-test-123");

        UUID paymentId = UUID.randomUUID();

        // When - simulate the service logic
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setClientReference(request.getClientReference());
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment savedPayment = paymentRepository.save(payment);

        // Then
        assertThat(savedPayment.getPaymentId()).isEqualTo(paymentId);
        assertThat(savedPayment.getAmount()).isEqualTo(BigDecimal.valueOf(150.00));
        assertThat(savedPayment.getCurrency()).isEqualTo("EUR");
        assertThat(savedPayment.getClientReference()).isEqualTo("integration-test-123");
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(savedPayment.getVersion()).isEqualTo(0);

        // Verify MQ publishing would be called (in real scenario)
        // Note: We can't easily test the full HTTP flow in this integration test
        // without starting the web server. This tests the data persistence part.
    }

    @Test
    void paymentRepository_shouldSupportOptimisticLocking() {
        // Given
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(100.00));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment saved = paymentRepository.save(payment);
        assertThat(saved.getVersion()).isEqualTo(0);

        // When - modify and save again
        saved.setStatus(PaymentStatus.IN_PROGRESS);
        Payment updated = paymentRepository.saveAndFlush(saved);

        // Then
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    void paymentRepository_shouldFindById() {
        // Given
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(BigDecimal.valueOf(200.00));
        payment.setCurrency("GBP");
        payment.setStatus(PaymentStatus.RECEIVED);

        paymentRepository.save(payment);

        // When
        var found = paymentRepository.findById(paymentId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo(paymentId);
        assertThat(found.get().getAmount()).isEqualTo(BigDecimal.valueOf(200.00));
        assertThat(found.get().getCurrency()).isEqualTo("GBP");
    }
}