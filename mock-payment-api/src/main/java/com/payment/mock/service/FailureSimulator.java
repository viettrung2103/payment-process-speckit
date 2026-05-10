package com.payment.mock.service;

import com.payment.mock.config.MockPaymentProperties;
import com.payment.mock.entity.TransactionErrorCode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class FailureSimulator {

    private final double failureRate;

    public FailureSimulator(MockPaymentProperties properties) {
        this.failureRate = properties.getFailureRate();

        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("mock.payment.failure-rate must be between 0.0 and 1.0");
        }
    }

    public boolean shouldFail() {
        return shouldFail(failureRate);
    }

    public boolean shouldFail(double failureRate) {
        return ThreadLocalRandom.current().nextDouble() < failureRate;
    }

    public TransactionErrorCode generateFailureScenario() {
        double random = ThreadLocalRandom.current().nextDouble();
        if (random < 0.02) {
            return TransactionErrorCode.TIMEOUT;
        }
        if (random < 0.04) {
            return TransactionErrorCode.VALIDATION_ERROR;
        }
        if (random < 0.06) {
            return TransactionErrorCode.RATE_LIMITED;
        }
        if (random < 0.08) {
            return TransactionErrorCode.INTERNAL_ERROR;
        }
        return TransactionErrorCode.SERVICE_UNAVAILABLE;
    }
}
