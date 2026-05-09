package com.payment.bridge.service;

import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentAudit;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAuditServiceTest {

    @Mock
    private PaymentAuditRepository paymentAuditRepository;

    @InjectMocks
    private PaymentAuditService paymentAuditService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setAmount(BigDecimal.valueOf(99.99));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
    }

    @Test
    void recordTransition_shouldSaveAuditEntry() {
        // Given
        when(paymentAuditRepository.save(any(PaymentAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        PaymentAudit audit = paymentAuditService.recordTransition(payment,
                PaymentStatus.RECEIVED,
                PaymentStatus.IN_PROGRESS,
                "Started processing",
                "worker-1");

        // Then
        assertThat(audit).isNotNull();
        assertThat(audit.getPaymentId()).isEqualTo(payment.getPaymentId());
        assertThat(audit.getOldStatus()).isEqualTo("RECEIVED");
        assertThat(audit.getNewStatus()).isEqualTo("IN_PROGRESS");
        assertThat(audit.getReason()).isEqualTo("Started processing");
        assertThat(audit.getChangedBy()).isEqualTo("worker-1");
        verify(paymentAuditRepository).save(any(PaymentAudit.class));
    }
}
