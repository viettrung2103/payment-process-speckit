package com.payment.bridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentRecoveryRunner {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRecoveryRunner.class);

    private final PaymentService paymentService;

    public PaymentRecoveryRunner(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application started, initiating recovery of in-progress payments");
        paymentService.recoverInProgressPayments();
    }
}
