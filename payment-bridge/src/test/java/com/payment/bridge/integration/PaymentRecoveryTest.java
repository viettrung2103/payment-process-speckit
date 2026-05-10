package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void clearPayments() {
        paymentRepository.deleteAll();
    }

    @MockBean
    private PaymentPublisher paymentPublisher;

    @MockBean
    private ExternalApiClient externalApiClient;

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
    void testRecoverInProgressTaskCompletedByExternalApi() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("150.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        paymentRepository.save(payment);

        ExternalApiClient.ApiResponse completedResponse = new ExternalApiClient.ApiResponse();
        completedResponse.setTransactionId(paymentId.toString());
        completedResponse.setStatus("COMPLETED");
        completedResponse.setStatusCode(200);
        completedResponse.setBody("{status=COMPLETED}");

        when(externalApiClient.getPaymentStatus(paymentId)).thenReturn(completedResponse);

        paymentService.recoverInProgressPayments();

        Optional<Payment> recoveredPayment = paymentRepository.findById(paymentId);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(recoveredPayment.get().getExternalTransactionId()).isEqualTo(paymentId.toString());
    }

    @Test
    void testRecoverInProgressTaskContinuesNormalProcessingWhenPending() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("175.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        paymentRepository.save(payment);

        ExternalApiClient.ApiResponse processingResponse = new ExternalApiClient.ApiResponse();
        processingResponse.setTransactionId(paymentId.toString());
        processingResponse.setStatus("PROCESSING");
        processingResponse.setStatusCode(200);
        processingResponse.setBody("{status=PROCESSING}");

        ExternalApiClient.ApiResponse completedResponse = new ExternalApiClient.ApiResponse();
        completedResponse.setTransactionId(paymentId.toString());
        completedResponse.setStatus("SUCCESS");
        completedResponse.setStatusCode(200);
        completedResponse.setBody("{status=SUCCESS}");

        when(externalApiClient.getPaymentStatus(paymentId)).thenReturn(processingResponse);
        when(externalApiClient.processPayment(any())).thenReturn(completedResponse);

        paymentService.recoverInProgressPayments();

        Optional<Payment> recoveredPayment = paymentRepository.findById(paymentId);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(recoveredPayment.get().getApiResponse()).contains("SUCCESS");
    }

    @Test
    void testRecoverInProgressTaskDefersRecoveryWhileServerDownThenCompletesAfterRestart() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("180.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        paymentRepository.save(payment);

        ExternalApiClient.ApiResponse completedResponse = new ExternalApiClient.ApiResponse();
        completedResponse.setTransactionId(paymentId.toString());
        completedResponse.setStatus("COMPLETED");
        completedResponse.setStatusCode(200);
        completedResponse.setBody("{status=COMPLETED}");

        when(externalApiClient.getPaymentStatus(paymentId))
            .thenThrow(new RuntimeException("Payment service down"))
            .thenReturn(completedResponse);

        // First recovery attempt: external service is down, so we should keep the payment IN_PROGRESS.
        paymentService.recoverInProgressPayments();
        Optional<Payment> firstCheck = paymentRepository.findById(paymentId);
        assertThat(firstCheck).isPresent();
        assertThat(firstCheck.get().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        // Simulate service restart and retry recovery.
        paymentService.recoverInProgressPayments();

        Optional<Payment> recoveredPayment = paymentRepository.findById(paymentId);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(recoveredPayment.get().getExternalTransactionId()).isEqualTo(paymentId.toString());
        assertThat(recoveredPayment.get().getApiResponse()).contains("COMPLETED");
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

    @Test
    void testComprehensiveRecoveryWhenServerDownThenRestartsWithThirdPartyPayment() {
        // PHASE 1: THIRD-PARTY INITIATES PAYMENT
        UUID paymentId = UUID.randomUUID();
        String clientReference = "third-party-payment-" + System.currentTimeMillis();

        PaymentRequest thirdPartyRequest = new PaymentRequest();
        thirdPartyRequest.setAmount(new BigDecimal("500.00"));
        thirdPartyRequest.setCurrency("USD");
        thirdPartyRequest.setClientReference(clientReference);

        var paymentResponse = paymentService.createPayment(thirdPartyRequest, null);
        UUID createdPaymentId = paymentResponse.getPaymentId();

        // PHASE 2: VERIFY PAYMENT IN DATABASE - RECEIVED
        Optional<Payment> createdPayment = paymentRepository.findById(createdPaymentId);
        assertThat(createdPayment)
            .as("Third-party payment should be created in database")
            .isPresent();
        assertThat(createdPayment.get().getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(createdPayment.get().getClientReference()).isEqualTo(clientReference);

        // PHASE 3: TRANSITION TO IN_PROGRESS
        Payment inProgressPayment = createdPayment.get();
        inProgressPayment.setStatus(PaymentStatus.IN_PROGRESS);
        paymentRepository.save(inProgressPayment);

        Optional<Payment> inProgressCheck = paymentRepository.findById(createdPaymentId);
        assertThat(inProgressCheck)
            .as("IN_PROGRESS payment should exist in database")
            .isPresent();
        assertThat(inProgressCheck.get().getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);

        // PHASE 4: SIMULATE SERVER DOWN - RECOVERY DEFERS
        when(externalApiClient.getPaymentStatus(createdPaymentId))
            .thenReturn(null);  // Simulates server not responding

        paymentService.recoverInProgressPayments();

        Optional<Payment> afterServerDownCheck = paymentRepository.findById(createdPaymentId);
        assertThat(afterServerDownCheck).isPresent();
        assertThat(afterServerDownCheck.get().getStatus())
            .as("Payment should remain IN_PROGRESS when status check fails")
            .isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(afterServerDownCheck.get().getExternalTransactionId())
            .as("No transaction ID when recovery deferred")
            .isNull();

        // PHASE 5: SERVER RESTARTS - RECOVERY COMPLETES
        ExternalApiClient.ApiResponse completedResponse = new ExternalApiClient.ApiResponse();
        completedResponse.setTransactionId(createdPaymentId.toString());
        completedResponse.setStatus("COMPLETED");
        completedResponse.setStatusCode(200);
        completedResponse.setBody("{\"status\": \"COMPLETED\"}");

        when(externalApiClient.getPaymentStatus(createdPaymentId))
            .thenReturn(completedResponse);

        paymentService.recoverInProgressPayments();

        // PHASE 6: VERIFY RECOVERY SUCCESSFUL AND DATA PERSISTED
        Optional<Payment> recoveredPayment = paymentRepository.findById(createdPaymentId);
        assertThat(recoveredPayment)
            .as("Payment exists after recovery")
            .isPresent();

        Payment finalPayment = recoveredPayment.get();
        assertThat(finalPayment.getStatus())
            .as("Payment completed after server restart")
            .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(finalPayment.getExternalTransactionId())
            .as("External transaction ID captured")
            .isEqualTo(createdPaymentId.toString());
        assertThat(finalPayment.getApiStatusCode()).isEqualTo(200);
        assertThat(finalPayment.getApiResponse()).contains("COMPLETED");

        // PHASE 7: VERIFY THIRD-PARTY DATA PRESERVED
        assertThat(finalPayment.getClientReference())
            .as("Client reference preserved from third-party request")
            .isEqualTo(clientReference);
        assertThat(finalPayment.getAmount())
            .as("Amount preserved from third-party request")
            .isEqualByComparingTo(new BigDecimal("500.00"));

        // PHASE 8: VERIFY DATABASE QUERIES WORK
        Optional<Payment> byClientRef = paymentRepository.findByClientReference(clientReference);
        assertThat(byClientRef)
            .as("Can retrieve payment by client reference after recovery")
            .isPresent();
        assertThat(byClientRef.get().getPaymentId()).isEqualTo(createdPaymentId);
        assertThat(byClientRef.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }
}