package com.payment.bridge.repository;

import com.payment.bridge.model.PaymentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, UUID> {
    List<PaymentAudit> findByPaymentIdOrderByChangedAtDesc(UUID paymentId);
}