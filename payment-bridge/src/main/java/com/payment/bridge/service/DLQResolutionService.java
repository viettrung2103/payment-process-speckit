package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentTaskPublisher;
import com.payment.bridge.model.DLQActionRequest;
import com.payment.bridge.model.DeadLetterQueueEntry;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import com.payment.bridge.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DLQResolutionService {

    private static final Logger logger = LoggerFactory.getLogger(DLQResolutionService.class);

    private final DeadLetterQueueRepository dlqRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTaskPublisher paymentTaskPublisher;
    private final PaymentAuditService paymentAuditService;

    public DLQResolutionService(DeadLetterQueueRepository dlqRepository,
                                PaymentRepository paymentRepository,
                                @Qualifier("paymentPublisher") PaymentTaskPublisher paymentTaskPublisher,
                                PaymentAuditService paymentAuditService) {
        this.dlqRepository = dlqRepository;
        this.paymentRepository = paymentRepository;
        this.paymentTaskPublisher = paymentTaskPublisher;
        this.paymentAuditService = paymentAuditService;
    }

    public List<DeadLetterQueueEntry> searchDLQEntries(Optional<UUID> paymentId, Optional<String> failedAction) {
        if (paymentId.isPresent() && failedAction.isPresent()) {
            return dlqRepository.findByPaymentIdAndFailedAction(paymentId.get(), failedAction.get());
        }
        if (paymentId.isPresent()) {
            return dlqRepository.findByPaymentId(paymentId.get());
        }
        if (failedAction.isPresent()) {
            return dlqRepository.findByFailedAction(failedAction.get());
        }
        return dlqRepository.findAll();
    }

    public DeadLetterQueueEntry getDLQEntry(UUID dlqId) {
        return dlqRepository.findById(dlqId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ entry not found: " + dlqId));
    }

    @Transactional
    public void retryDLQEntry(UUID dlqId, DLQActionRequest request) {
        DeadLetterQueueEntry entry = getDLQEntry(dlqId);
        Payment payment = paymentRepository.findById(entry.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("Payment not found for DLQ entry: " + entry.getPaymentId()));

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Cannot retry a completed payment: " + payment.getPaymentId());
        }

        PaymentStatus previousStatus = payment.getStatus();
        payment.setStatus(PaymentStatus.RECEIVED);
        payment.setErrorReason(null);
        payment.setErrorDetails(null);
        paymentRepository.save(payment);

        paymentAuditService.recordTransition(payment, previousStatus, PaymentStatus.RECEIVED,
                "Manual retry from DLQ by " + request.getOperator(), "dlq-resolution");

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(payment.getPaymentId());
        task.setAction("PROCESS_PAYMENT");
        task.setRetryAttempt(0);
        task.setEnqueuedAt(Instant.now());
        task.setDequeueCount(0);

        paymentTaskPublisher.publishPaymentTask(task);
        dlqRepository.delete(entry);

        logger.info("Retried DLQ entry {} for payment {} by operator {}", dlqId, payment.getPaymentId(), request.getOperator());
    }

    @Transactional
    public void resolveDLQEntry(UUID dlqId, DLQActionRequest request) {
        DeadLetterQueueEntry entry = getDLQEntry(dlqId);
        dlqRepository.delete(entry);
        logger.info("Resolved DLQ entry {} by operator {}: {}", dlqId, request.getOperator(), request.getResolutionDetails());
    }
}