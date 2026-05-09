package com.payment.mock.service;

import com.payment.mock.config.MockPaymentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DelaySimulator Tests")
class DelaySimulatorTest {

    private final MockPaymentProperties properties = new MockPaymentProperties();
    {
        MockPaymentProperties.Delay delay = new MockPaymentProperties.Delay();
        delay.setMin(10);
        delay.setMax(2000);
        properties.setDelay(delay);
    }

    private final DelaySimulator delaySimulator = new DelaySimulator(properties);

    @Test
    @DisplayName("applyDelay completes without exception")
    void testApplyDelayCompletes() {
        assertDoesNotThrow(() -> delaySimulator.applyDelay(10));
    }

    @Test
    @DisplayName("applyDelay respects minimum delay")
    void testDelayRespectsMinimum() {
        long startTime = System.currentTimeMillis();
        delaySimulator.applyDelay(10);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertTrue(elapsedMs >= 8,
                String.format("Elapsed time %dms is less than expected minimum", elapsedMs));
    }

    @Test
    @DisplayName("applyDelay respects maximum delay")
    void testDelayRespectsMaximum() {
        long startTime = System.currentTimeMillis();
        delaySimulator.applyDelay(2000);
        long elapsedMs = System.currentTimeMillis() - startTime;

        assertTrue(elapsedMs <= 2100,
                String.format("Elapsed time %dms exceeds expected maximum", elapsedMs));
    }
}
