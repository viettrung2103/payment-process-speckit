package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentTaskPublisher;
import com.payment.bridge.model.DLQActionRequest;
import com.payment.bridge.model.DeadLetterQueueEntry;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DLQResolutionServiceTest {

    @Mock
    private DeadLetterQueueRepository dlqRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTaskPublisher paymentTaskPublisher;

    @Mock
    private PaymentAuditService paymentAuditService;

    @InjectMocks
    private DLQResolutionService dlqResolutionService;

    private UUID paymentId;
    private UUID dlqId;
    private DeadLetterQueueEntry dlqEntry;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        dlqId = UUID.randomUUID();

        dlqEntry = new DeadLetterQueueEntry();
        dlqEntry.setDlqId(dlqId);
        dlqEntry.setPaymentId(paymentId);
        dlqEntry.setFailedAction("PROCESS_PAYMENT");
        dlqEntry.setFailureReason("Timeout");
        dlqEntry.setPaymentContext("{}\n");
        dlqEntry.setRetryHistory("[]");

        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setStatus(PaymentStatus.FAILED);
    }

    @Test
    void searchDLQEntries_WithPaymentIdAndFailedAction_CallsRepository() {
        when(dlqRepository.findByPaymentIdAndFailedAction(paymentId, "PROCESS_PAYMENT"))
                .thenReturn(java.util.List.of(dlqEntry));

        var results = dlqResolutionService.searchDLQEntries(Optional.of(paymentId), Optional.of("PROCESS_PAYMENT"));

        assertThat(results).containsExactly(dlqEntry);
        verify(dlqRepository).findByPaymentIdAndFailedAction(paymentId, "PROCESS_PAYMENT");
        verifyNoMoreInteractions(dlqRepository);
    }

    @Test
    void retryDLQEntry_ResetsPaymentAndPublishesTask() {
        DLQActionRequest request = new DLQActionRequest("operator-1", "retry now");

        when(dlqRepository.findById(dlqId)).thenReturn(Optional.of(dlqEntry));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dlqResolutionService.retryDLQEntry(dlqId, request);

        verify(dlqRepository).findById(dlqId);
        verify(paymentRepository).findById(paymentId);
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentTaskPublisher).publishPaymentTask(any());
        verify(dlqRepository).delete(dlqEntry);
        verify(paymentAuditService).recordTransition(payment, PaymentStatus.FAILED, PaymentStatus.RECEIVED,
                "Manual retry from DLQ by operator-1", "dlq-resolution");
    }

    @Test
    void retryDLQEntry_WhenPaymentCompleted_Throws() {
        payment.setStatus(PaymentStatus.COMPLETED);
        dlqEntry.setPaymentId(paymentId);

        when(dlqRepository.findById(dlqId)).thenReturn(Optional.of(dlqEntry));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> dlqResolutionService.retryDLQEntry(dlqId, new DLQActionRequest("ops", "retry")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed payment");
    }

    @Test
    void resolveDLQEntry_DeletesEntry() {
        DLQActionRequest request = new DLQActionRequest("operator-2", "ignore duplicate");

        when(dlqRepository.findById(dlqId)).thenReturn(Optional.of(dlqEntry));

        dlqResolutionService.resolveDLQEntry(dlqId, request);

        verify(dlqRepository).findById(dlqId);
        verify(dlqRepository).delete(dlqEntry);
    }

    @Test
    void getDLQEntry_NotFound_Throws() {
        when(dlqRepository.findById(dlqId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dlqResolutionService.getDLQEntry(dlqId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DLQ entry not found");
    }
}
