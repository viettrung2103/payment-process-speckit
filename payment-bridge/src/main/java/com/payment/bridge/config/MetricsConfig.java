package com.payment.bridge.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public JvmGcMetrics jvmGcMetrics(MeterRegistry meterRegistry) {
        JvmGcMetrics jvmGcMetrics = new JvmGcMetrics();
        jvmGcMetrics.bindTo(meterRegistry);
        return jvmGcMetrics;
    }

    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics(MeterRegistry meterRegistry) {
        JvmMemoryMetrics jvmMemoryMetrics = new JvmMemoryMetrics();
        jvmMemoryMetrics.bindTo(meterRegistry);
        return jvmMemoryMetrics;
    }

    @Bean
    public JvmThreadMetrics jvmThreadMetrics(MeterRegistry meterRegistry) {
        JvmThreadMetrics jvmThreadMetrics = new JvmThreadMetrics();
        jvmThreadMetrics.bindTo(meterRegistry);
        return jvmThreadMetrics;
    }

    @Bean
    public ProcessorMetrics processorMetrics(MeterRegistry meterRegistry) {
        ProcessorMetrics processorMetrics = new ProcessorMetrics();
        processorMetrics.bindTo(meterRegistry);
        return processorMetrics;
    }
}