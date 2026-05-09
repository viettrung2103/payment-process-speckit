package com.payment.bridge.service;

import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentAudit;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentAuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentAuditService {

    private final PaymentAuditRepository paymentAuditRepository;

    public PaymentAuditService(PaymentAuditRepository paymentAuditRepository) {
        this.paymentAuditRepository = paymentAuditRepository;
    }

    public PaymentAudit recordTransition(Payment payment,
                                         PaymentStatus oldStatus,
                                         PaymentStatus newStatus,
                                         String reason,
                                         String changedBy) {
        PaymentAudit audit = new PaymentAudit();
        audit.setAuditId(UUID.randomUUID());
        audit.setPaymentId(payment.getPaymentId());
        audit.setOldStatus(oldStatus != null ? oldStatus.name() : null);
        audit.setNewStatus(newStatus != null ? newStatus.name() : null);
        audit.setReason(reason);
        audit.setChangedBy(changedBy);
        return paymentAuditRepository.save(audit);
    }

    public List<PaymentAudit> getAuditTrail(UUID paymentId) {
        return paymentAuditRepository.findByPaymentIdOrderByChangedAtDesc(paymentId);
    }
}
