package com.payment.bridge.service;

import com.payment.bridge.exception.IdempotencyViolationException;
import com.payment.bridge.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    // In production, this would be a distributed cache like Redis
    // For now, using in-memory map for simplicity
    private final Map<String, Payment> idempotencyCache = new ConcurrentHashMap<>();

    /**
     * Checks for idempotency based on client_reference or request signature.
     * Returns existing payment if found, empty if new request.
     * Throws IdempotencyViolationException for conflicting requests.
     */
    public Optional<Payment> checkIdempotency(String idempotencyKey, Payment newPayment) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            // No idempotency key provided, allow the request
            return Optional.empty();
        }

        Payment existingPayment = idempotencyCache.get(idempotencyKey);

        if (existingPayment == null) {
            // First time seeing this key, cache the payment for future checks
            idempotencyCache.put(idempotencyKey, newPayment);
            logger.debug("New idempotent request with key: {}", idempotencyKey);
            return Optional.empty();
        }

        // Check if the request matches the existing payment
        if (paymentsMatch(existingPayment, newPayment)) {
            logger.debug("Idempotent request matched existing payment: {}", existingPayment.getPaymentId());
            return Optional.of(existingPayment);
        } else {
            logger.warn("Idempotency violation detected for key: {}", idempotencyKey);
            throw new IdempotencyViolationException(
                "Idempotency key '" + idempotencyKey + "' already used for a different payment request"
            );
        }
    }

    private boolean paymentsMatch(Payment existing, Payment newPayment) {
        return existing.getAmount().equals(newPayment.getAmount()) &&
               existing.getCurrency().equals(newPayment.getCurrency()) &&
               existing.getClientReference().equals(newPayment.getClientReference());
    }

    /**
     * Generates an idempotency key from request headers or content.
     * In production, this might use X-Idempotency-Key header or request signature.
     */
    public String generateIdempotencyKey(String clientReference, String requestSignature) {
        if (clientReference != null && !clientReference.trim().isEmpty()) {
            return "client_ref:" + clientReference;
        }
        if (requestSignature != null && !requestSignature.trim().isEmpty()) {
            return "signature:" + requestSignature;
        }
        return null; // No idempotency key available
    }
}