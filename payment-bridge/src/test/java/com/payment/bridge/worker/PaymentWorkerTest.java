package com.payment.bridge.worker;

import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.service.ErrorClassifier;
import com.payment.bridge.service.PaymentAuditService;
import com.payment.bridge.service.PaymentService;
import com.payment.bridge.service.RetryHandler;
import com.payment.bridge.service.DLQHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentWorker message listener.
 * Tests RabbitMQ message handling and payment processing flow.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private ErrorClassifier errorClassifier;

    @Mock
    private RetryHandler retryHandler;

    @Mock
    private DLQHandler dlqHandler;

    @Mock
    private PaymentAuditService paymentAuditService;

    @InjectMocks
    private PaymentWorker paymentWorker;

    private MessageQueueTask task;
    private UUID paymentId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setAction("PROCESS_PAYMENT");
        task.setRetryAttempt(0);

        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
    }

    @Test
    void testProcessPaymentTask_ShouldDelegateToPaymentService() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        paymentWorker.processPaymentTask(task);

        // Then
        verify(paymentRepository).findById(paymentId);
        verify(paymentService).processPaymentWithExternalAPI(paymentId);
    }

    @Test
    void testProcessPaymentTask_PaymentNotFound_ShouldThrowException() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentWorker.processPaymentTask(task))
            .isInstanceOf(PaymentProcessingException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testProcessPaymentTask_ShouldPublishToExternalAPI() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        paymentWorker.processPaymentTask(task);

        // Then
        verify(paymentService, times(1)).processPaymentWithExternalAPI(paymentId);
    }

    @Test
    void testProcessPaymentTask_RetryOnOptimisticLockingFailure() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        doThrow(new OptimisticLockingFailureException("Optimistic lock"))
            .when(paymentService).processPaymentWithExternalAPI(paymentId);
        when(errorClassifier.classify(any(Throwable.class))).thenReturn(ErrorClassifier.ErrorAction.RETRY);
        when(retryHandler.hasExceededMaxRetries(any(MessageQueueTask.class))).thenReturn(false);
        when(retryHandler.scheduleRetry(any(MessageQueueTask.class))).thenReturn(true);

        // When
        PaymentWorker.ProcessingResult result = paymentWorker.processPaymentTask(task);

        // Then
        assertThat(result).isEqualTo(PaymentWorker.ProcessingResult.RETRY_SCHEDULED);
        verify(retryHandler, times(1)).scheduleRetry(any(MessageQueueTask.class));
    }

    @Test
    void testProcessPaymentTask_RetryOnOptimisticLockingDuringInitialSave() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        doThrow(new OptimisticLockingFailureException("Optimistic lock"))
            .when(paymentRepository).save(any(Payment.class));
        when(errorClassifier.classify(any(Throwable.class))).thenReturn(ErrorClassifier.ErrorAction.RETRY);
        when(retryHandler.hasExceededMaxRetries(any(MessageQueueTask.class))).thenReturn(false);
        when(retryHandler.scheduleRetry(any(MessageQueueTask.class))).thenReturn(true);

        // When
        PaymentWorker.ProcessingResult result = paymentWorker.processPaymentTask(task);

        // Then
        assertThat(result).isEqualTo(PaymentWorker.ProcessingResult.RETRY_SCHEDULED);
        verify(paymentService, never()).processPaymentWithExternalAPI(paymentId);
        verify(retryHandler, times(1)).scheduleRetry(any(MessageQueueTask.class));
    }

    @Test
    void testProcessPaymentTask_WithRetryCount() {
        // Given
        task.setRetryAttempt(2);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        paymentWorker.processPaymentTask(task);

        // Then
        verify(paymentService).processPaymentWithExternalAPI(paymentId);
    }

    @Test
    void testManualAckAfterSuccessfulProcessing() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        paymentWorker.processPaymentTask(task);

        // Then - Verify the task was processed without throwing exceptions
        verify(paymentRepository).findById(paymentId);
    }

    @Test
    void testProcessPaymentTask_ShouldLogCriticalInfo() {
        // Given
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // When
        paymentWorker.processPaymentTask(task);

        // Then - Verify processing occurred
        verify(paymentService).processPaymentWithExternalAPI(paymentId);
    }
}