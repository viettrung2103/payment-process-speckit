package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.exception.IdempotencyViolationException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PaymentPublisher paymentPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private PaymentRequest validRequest;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        validRequest = new PaymentRequest();
        validRequest.setAmount(BigDecimal.valueOf(100.00));
        validRequest.setCurrency("USD");
        validRequest.setClientReference("test-ref-123");
    }

    @Test
    void createPayment_shouldCreateNewPayment_whenIdempotencyCheckPasses() {
        // Given
        when(idempotencyService.checkIdempotency(any(), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(paymentId);
            return payment;
        });

        // When
        PaymentResponse response = paymentService.createPayment(validRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo("RECEIVED");
        assertThat(response.getMessage()).isEqualTo("Payment request accepted for processing");
        assertThat(response.getEstimatedProcessingTime()).isEqualTo("PT30S");
        verify(paymentRepository).save(any(Payment.class));
        verify(idempotencyService).checkIdempotency(any(), any());
        verify(paymentPublisher).publishPaymentTask(any(MessageQueueTask.class));
    }

    @Test
    void createPayment_shouldReturnExistingPayment_whenIdempotentRequestDetected() {
        // Given
        Payment existingPayment = new Payment();
        existingPayment.setPaymentId(paymentId);
        existingPayment.setStatus(com.payment.bridge.model.PaymentStatus.COMPLETED);
        existingPayment.setAmount(BigDecimal.valueOf(100.00));
        existingPayment.setCurrency("USD");

        when(idempotencyService.checkIdempotency(any(), any())).thenReturn(Optional.of(existingPayment));

        // When
        PaymentResponse response = paymentService.createPayment(validRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getMessage()).isEqualTo("Payment request accepted for processing");
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(idempotencyService).checkIdempotency(any(), any());
    }

    @Test
    void createPayment_shouldThrowIdempotencyViolation_whenDuplicateDetected() {
        // Given
        when(idempotencyService.checkIdempotency(any(), any()))
            .thenThrow(new IdempotencyViolationException("Duplicate payment detected"));

        // When & Then
        assertThatThrownBy(() -> paymentService.createPayment(validRequest))
            .isInstanceOf(IdempotencyViolationException.class)
            .hasMessage("Duplicate payment detected");

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_shouldValidateAmount() {
        // Given
        validRequest.setAmount(BigDecimal.ZERO);

        // When & Then
        assertThatThrownBy(() -> paymentService.createPayment(validRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }

    @Test
    void createPayment_shouldValidateCurrency() {
        // Given
        validRequest.setCurrency("INVALID");

        // When & Then
        assertThatThrownBy(() -> paymentService.createPayment(validRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    @Test
    void createPayment_shouldUseExplicitIdempotencyHeader_whenProvided() {
        // Given
        String idempotencyKey = "header-key-123";
        when(idempotencyService.checkIdempotency(eq(idempotencyKey), any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setPaymentId(paymentId);
            return payment;
        });

        // When
        PaymentResponse response = paymentService.createPayment(validRequest, idempotencyKey);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
        verify(idempotencyService).checkIdempotency(eq(idempotencyKey), any());
    }
}