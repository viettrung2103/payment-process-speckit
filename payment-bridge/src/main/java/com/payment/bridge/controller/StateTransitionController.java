package com.payment.bridge.controller;

import com.payment.bridge.model.PaymentAudit;
import com.payment.bridge.service.PaymentAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class StateTransitionController {

    private static final Logger logger = LoggerFactory.getLogger(StateTransitionController.class);

    private final PaymentAuditService paymentAuditService;

    public StateTransitionController(PaymentAuditService paymentAuditService) {
        this.paymentAuditService = paymentAuditService;
    }

    @GetMapping("/{paymentId}/audit")
    public ResponseEntity<List<PaymentAudit>> getPaymentAudit(@PathVariable UUID paymentId) {
        logger.info("Fetching audit trail for payment {}", paymentId);
        List<PaymentAudit> auditTrail = paymentAuditService.getAuditTrail(paymentId);
        return ResponseEntity.ok(auditTrail);
    }
}
