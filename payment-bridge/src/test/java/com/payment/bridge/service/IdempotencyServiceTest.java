package com.payment.bridge.service;

import com.payment.bridge.exception.IdempotencyViolationException;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
    }

    @Test
    void checkIdempotency_shouldReturnEmptyForNewKey() {
        // Given
        Payment payment = createTestPayment();

        // When
        Optional<Payment> result = idempotencyService.checkIdempotency("new-key", payment);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void checkIdempotency_shouldReturnExistingPaymentForMatchingRequest() {
        // Given
        Payment originalPayment = createTestPayment();
        String key = "test-key";

        // First call - should cache the payment
        idempotencyService.checkIdempotency(key, originalPayment);

        // Second call with same key and matching payment
        Payment matchingPayment = createTestPayment();
        Optional<Payment> result = idempotencyService.checkIdempotency(key, matchingPayment);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(originalPayment);
    }

    @Test
    void checkIdempotency_shouldThrowExceptionForConflictingRequest() {
        // Given
        Payment originalPayment = createTestPayment();
        String key = "test-key";

        // First call - cache the payment
        idempotencyService.checkIdempotency(key, originalPayment);

        // Second call with same key but different amount
        Payment conflictingPayment = createTestPayment();
        conflictingPayment.setAmount(BigDecimal.valueOf(200.00));

        // When & Then
        assertThatThrownBy(() -> idempotencyService.checkIdempotency(key, conflictingPayment))
            .isInstanceOf(IdempotencyViolationException.class)
            .hasMessageContaining("Idempotency key 'test-key' already used for a different payment request");
    }

    @Test
    void checkIdempotency_shouldAllowNullKey() {
        // Given
        Payment payment = createTestPayment();

        // When
        Optional<Payment> result = idempotencyService.checkIdempotency(null, payment);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void checkIdempotency_shouldAllowEmptyKey() {
        // Given
        Payment payment = createTestPayment();

        // When
        Optional<Payment> result = idempotencyService.checkIdempotency("", payment);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void generateIdempotencyKey_shouldUseClientReference() {
        // When
        String key = idempotencyService.generateIdempotencyKey("client-ref-123", null);

        // Then
        assertThat(key).isEqualTo("client_ref:client-ref-123");
    }

    @Test
    void generateIdempotencyKey_shouldUseRequestSignature() {
        // When
        String key = idempotencyService.generateIdempotencyKey(null, "signature-abc");

        // Then
        assertThat(key).isEqualTo("signature:signature-abc");
    }

    @Test
    void generateIdempotencyKey_shouldReturnNullWhenNoInputs() {
        // When
        String key = idempotencyService.generateIdempotencyKey(null, null);

        // Then
        assertThat(key).isNull();
    }

    @Test
    void generateIdempotencyKey_shouldPreferClientReference() {
        // When
        String key = idempotencyService.generateIdempotencyKey("client-ref", "signature");

        // Then
        assertThat(key).isEqualTo("client_ref:client-ref");
    }

    private Payment createTestPayment() {
        Payment payment = new Payment();
        payment.setPaymentId(java.util.UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(100.00));
        payment.setCurrency("USD");
        payment.setClientReference("test-ref");
        payment.setStatus(PaymentStatus.RECEIVED);
        return payment;
    }
}