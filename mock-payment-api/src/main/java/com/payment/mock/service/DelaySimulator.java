package com.payment.mock.service;

import com.payment.mock.config.MockPaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;

@Component
public class DelaySimulator {

    private static final Logger log = LoggerFactory.getLogger(DelaySimulator.class);

    private final long minDelayMs;
    private final long maxDelayMs;

    public DelaySimulator(MockPaymentProperties properties) {
        this.minDelayMs = properties.getDelay().getMin();
        this.maxDelayMs = properties.getDelay().getMax();

        if (minDelayMs < 0 || maxDelayMs < 0 || maxDelayMs < minDelayMs) {
            throw new IllegalArgumentException("mock.payment.delay range must be positive and min <= max");
        }
    }

    public long calculateRandomDelay() {
        return ThreadLocalRandom.current().nextLong(minDelayMs, maxDelayMs + 1);
    }

    public void applyDelay(long delayMs) {
        log.debug("Simulating processing delay: {}ms", delayMs);
        LockSupport.parkNanos(delayMs * 1_000_000);
    }
}
