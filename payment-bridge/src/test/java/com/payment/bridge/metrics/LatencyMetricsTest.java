package com.payment.bridge.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyMetricsTest {

    @Test
    void recordIngestionLatencyShouldCreateTimerEntry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LatencyMetrics metrics = new LatencyMetrics(registry);

        Timer.Sample sample = metrics.startIngestionTimer();
        metrics.recordIngestionLatency(sample);

        Timer timer = registry.find("payment.bridge.ingestion.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    void recordProcessingLatencyShouldCreateTimerEntry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LatencyMetrics metrics = new LatencyMetrics(registry);

        Timer.Sample sample = metrics.startProcessingTimer();
        metrics.recordProcessingLatency(sample);

        Timer timer = registry.find("payment.bridge.processing.latency").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }
}
