package com.payment.bridge.repository;

import com.payment.bridge.model.DeadLetterQueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueueEntry, UUID> {
    List<DeadLetterQueueEntry> findByPaymentId(UUID paymentId);
}