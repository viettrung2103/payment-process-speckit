package com.payment.mock.service;

import com.payment.mock.config.MockPaymentProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FailureSimulator Tests")
class FailureSimulatorTest {

    private final MockPaymentProperties properties = new MockPaymentProperties();
    {
        properties.setFailureRate(0.10);
    }

    private final FailureSimulator failureSimulator = new FailureSimulator(properties);

    @Test
    @DisplayName("shouldFail returns boolean value")
    void testShouldFailReturnsBoolean() {
        boolean result = failureSimulator.shouldFail();
        assertNotNull(result);
    }

    @Test
    @DisplayName("shouldFail returns approximately 10% true over 1000 samples")
    void testShouldFailDistribution() {
        int failCount = 0;
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            if (failureSimulator.shouldFail()) {
                failCount++;
            }
        }

        double failRate = (double) failCount / iterations;
        assertTrue(failRate >= 0.07 && failRate <= 0.13,
                String.format("Failure rate %.2f%% is outside expected range 7-13%%", failRate * 100));
    }
}
