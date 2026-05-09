package com.payment.bridge.controller;

import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class StatusController {

    private static final Logger logger = LoggerFactory.getLogger(StatusController.class);

    private final PaymentRepository paymentRepository;

    public StatusController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/status/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable UUID paymentId) {
        logger.info("Retrieving payment status for: {}", paymentId);

        Optional<Payment> paymentOpt = paymentRepository.findById(paymentId);

        if (paymentOpt.isEmpty()) {
            logger.warn("Payment not found: {}", paymentId);
            return ResponseEntity.notFound().build();
        }

        Payment payment = paymentOpt.get();
        PaymentResponse response = new PaymentResponse();
        response.setPaymentId(payment.getPaymentId());
        response.setStatus(payment.getStatus().name());
        response.setMessage("Payment status retrieved successfully");

        // Add basic HATEOAS links
        response.setLinks(java.util.Map.of(
            "self", java.util.Map.of("href", "/api/v1/payments/status/" + paymentId)
        ));

        logger.debug("Payment status retrieved: {} - {}", paymentId, payment.getStatus());
        return ResponseEntity.ok(response);
    }
}