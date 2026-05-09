package com.payment.mock.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mock.payment")
public class MockPaymentProperties {

    @Min(value = 0, message = "failureRate must be between 0.0 and 1.0")
    @Max(value = 1, message = "failureRate must be between 0.0 and 1.0")
    private double failureRate = 0.1;

    @Valid
    @NotNull
    private Delay delay = new Delay();

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public Delay getDelay() {
        return delay;
    }

    public void setDelay(Delay delay) {
        this.delay = delay;
    }

    public static class Delay {

        @Min(value = 0, message = "delay.min must be non-negative")
        private long min = 1;

        @Min(value = 0, message = "delay.max must be non-negative")
        private long max = 100;

        public long getMin() {
            return min;
        }

        public void setMin(long min) {
            this.min = min;
        }

        public long getMax() {
            return max;
        }

        public void setMax(long max) {
            this.max = max;
        }

        @AssertTrue(message = "delay.max must be greater than or equal to delay.min")
        public boolean isValidRange() {
            return max >= min;
        }
    }
}
