package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for system crash recovery scenarios.
 * Verifies that payments persisted in DB can be recovered even if MQ publishing fails.
 */
@SpringBootTest
@ActiveProfiles("test")
class PaymentRecoveryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @MockBean
    private PaymentPublisher paymentPublisher;

    @Test
    void testPaymentRecoveryAfterDatabasePersistence() {
        // Given: A payment has been persisted to the database in RECEIVED state
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment savedPayment = paymentRepository.save(payment);

        // When: Simulate system crash (clear caches, restart) - in reality this would be a full app restart
        // The payment should be retrievable from the database

        // Then: Payment should be retrievable from database
        Optional<Payment> recoveredPayment = paymentRepository.findById(paymentId);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getPaymentId()).isEqualTo(paymentId);
        assertThat(recoveredPayment.get().getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(recoveredPayment.get().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void testIdempotencyKeyRecoveryAfterCrash() {
        // Given: Multiple payments with the same client reference
        String clientReference = "recovery-test-ref-" + System.currentTimeMillis();

        UUID paymentId1 = UUID.randomUUID();
        Payment payment1 = new Payment();
        payment1.setPaymentId(paymentId1);
        payment1.setAmount(new BigDecimal("100.00"));
        payment1.setCurrency("USD");
        payment1.setClientReference(clientReference);
        payment1.setStatus(PaymentStatus.RECEIVED);

        Payment savedPayment1 = paymentRepository.save(payment1);

        // When: Simulate crash and recovery, system tries to create same payment again
        // The system should detect it's a duplicate based on client reference

        // Then: The same payment should be retrievable by client reference
        Optional<Payment> recoveredPayment = paymentRepository.findByClientReference(clientReference);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getPaymentId()).isEqualTo(paymentId1);
    }

    @Test
    void testMultiplePaymentsRecoveryAfterCrash() {
        // Given: Multiple payments in the database at various statuses
        UUID paymentId1 = UUID.randomUUID();
        Payment payment1 = new Payment();
        payment1.setPaymentId(paymentId1);
        payment1.setAmount(new BigDecimal("100.00"));
        payment1.setCurrency("USD");
        payment1.setStatus(PaymentStatus.RECEIVED);

        UUID paymentId2 = UUID.randomUUID();
        Payment payment2 = new Payment();
        payment2.setPaymentId(paymentId2);
        payment2.setAmount(new BigDecimal("200.00"));
        payment2.setCurrency("EUR");
        payment2.setStatus(PaymentStatus.IN_PROGRESS);

        UUID paymentId3 = UUID.randomUUID();
        Payment payment3 = new Payment();
        payment3.setPaymentId(paymentId3);
        payment3.setAmount(new BigDecimal("300.00"));
        payment3.setCurrency("GBP");
        payment3.setStatus(PaymentStatus.COMPLETED);

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        paymentRepository.save(payment3);

        // When: After crash recovery, all payments should be retrievable
        long totalCount = paymentRepository.count();

        // Then: All payments should be in the database
        assertThat(totalCount).isGreaterThanOrEqualTo(3);

        Optional<Payment> recovered1 = paymentRepository.findById(paymentId1);
        Optional<Payment> recovered2 = paymentRepository.findById(paymentId2);
        Optional<Payment> recovered3 = paymentRepository.findById(paymentId3);

        assertThat(recovered1).isPresent().get()
            .extracting(Payment::getStatus)
            .isEqualTo(PaymentStatus.RECEIVED);

        assertThat(recovered2).isPresent().get()
            .extracting(Payment::getStatus)
            .isEqualTo(PaymentStatus.IN_PROGRESS);

        assertThat(recovered3).isPresent().get()
            .extracting(Payment::getStatus)
            .isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void testOptimisticLockingAfterRecovery() {
        // Given: A payment in the database with version = 0
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        payment.setVersion(0);

        Payment saved = paymentRepository.save(payment);
        assertThat(saved.getVersion()).isEqualTo(0);

        // When: After recovery, retrieve and update the payment
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();

        Payment toUpdate = retrieved.get();
        toUpdate.setStatus(PaymentStatus.IN_PROGRESS);
        Payment updated = paymentRepository.save(toUpdate);

        // Then: Version should be incremented
        assertThat(updated.getVersion()).isEqualTo(1);

        // Verify the update persisted
        Optional<Payment> verified = paymentRepository.findById(paymentId);
        assertThat(verified.get().getVersion()).isEqualTo(1);
        assertThat(verified.get().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
    }

    @Test
    @Transactional
    void testPaymentPersistenceWithoutMQPublishFailure() {
        // Given: A payment request that should be persisted even if MQ publishing fails
        String clientReference = "recovery-api-failure-" + System.currentTimeMillis();

        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("USD");
        request.setClientReference(clientReference);

        doThrow(new RuntimeException("MQ failure") )
            .when(paymentPublisher).publishPaymentTask(any(MessageQueueTask.class));

        // When: createPayment should persist payment and swallow MQ publish failure
        var response = paymentService.createPayment(request, null);

        // Then: Payment persisted in DB and response returned
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("RECEIVED");
        assertThat(response.getPaymentId()).isNotNull();

        Optional<Payment> persisted = paymentRepository.findById(response.getPaymentId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    }
}