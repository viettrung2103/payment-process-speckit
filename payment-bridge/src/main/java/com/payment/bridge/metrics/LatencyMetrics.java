package com.payment.bridge.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LatencyMetrics {

    private final Timer ingestionTimer;
    private final Timer processingTimer;

    public LatencyMetrics(MeterRegistry meterRegistry) {
        this.ingestionTimer = Timer.builder("payment.bridge.ingestion.latency")
            .description("Latency for payment ingestion request handling")
            .publishPercentileHistogram(true)
            .register(meterRegistry);

        this.processingTimer = Timer.builder("payment.bridge.processing.latency")
            .description("Latency for external payment processing")
            .publishPercentileHistogram(true)
            .register(meterRegistry);
    }

    public Timer.Sample startIngestionTimer() {
        return Timer.start();
    }

    public Duration recordIngestionLatency(Timer.Sample sample) {
        long nanos = sample.stop(ingestionTimer);
        return Duration.ofNanos(nanos);
    }

    public Timer.Sample startProcessingTimer() {
        return Timer.start();
    }

    public Duration recordProcessingLatency(Timer.Sample sample) {
        long nanos = sample.stop(processingTimer);
        return Duration.ofNanos(nanos);
    }
}
