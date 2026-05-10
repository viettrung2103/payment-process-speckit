package com.payment.bridge.repository;

import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByClientReference(String clientReference);
    List<Payment> findByStatus(PaymentStatus status);
}
