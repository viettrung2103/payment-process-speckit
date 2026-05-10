package com.payment.scaling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TDD Unit Tests for Auto-Scaling Logic
 * Following RED-GREEN-REFACTOR cycle
 */
@DisplayName("Auto-Scaling Logic")
class AutoScalerTest {

    @Mock
    private MetricsProvider metricsProvider;

    @Mock
    private InstanceManager instanceManager;

    private AutoScaler autoScaler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        autoScaler = new AutoScaler(metricsProvider, instanceManager, 1, 5, 70.0, 30.0, 300);
    }

    @Test
    @DisplayName("Should scale UP when CPU usage exceeds threshold")
    void shouldScaleUpWhenCpuHigh() {
        // Given: High CPU usage
        when(metricsProvider.getAverageCpuUsage()).thenReturn(75.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should recommend scale up
        assertThat(decision.getAction()).isEqualTo(ScalingAction.SCALE_UP);
        assertThat(decision.getTargetInstances()).isEqualTo(3);
        assertThat(decision.getReason()).contains("CPU usage 75.0% > 70.0%");
    }

    @Test
    @DisplayName("Should scale DOWN when CPU usage below threshold")
    void shouldScaleDownWhenCpuLow() {
        // Given: Low CPU usage
        when(metricsProvider.getAverageCpuUsage()).thenReturn(25.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(3);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should recommend scale down
        assertThat(decision.getAction()).isEqualTo(ScalingAction.SCALE_DOWN);
        assertThat(decision.getTargetInstances()).isEqualTo(2);
        assertThat(decision.getReason()).contains("CPU usage 25.0% < 30.0%");
    }

    @Test
    @DisplayName("Should NOT scale when CPU usage in optimal range")
    void shouldNotScaleWhenCpuOptimal() {
        // Given: CPU usage in optimal range
        when(metricsProvider.getAverageCpuUsage()).thenReturn(50.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should not scale
        assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
        assertThat(decision.getReason()).contains("CPU usage 50.0% within optimal range");
    }

    @Test
    @DisplayName("Should NOT scale UP beyond maximum instances")
    void shouldNotScaleUpBeyondMaximum() {
        // Given: Already at maximum instances
        when(metricsProvider.getAverageCpuUsage()).thenReturn(80.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(5);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should not scale up
        assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
        assertThat(decision.getReason()).contains("Already at maximum instances (5)");
    }

    @Test
    @DisplayName("Should NOT scale DOWN below minimum instances")
    void shouldNotScaleDownBelowMinimum() {
        // Given: Already at minimum instances
        when(metricsProvider.getAverageCpuUsage()).thenReturn(20.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(1);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should not scale down
        assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
        assertThat(decision.getReason()).contains("Already at minimum instances (1)");
    }

    @Test
    @DisplayName("Should NOT scale during cooldown period")
    void shouldNotScaleDuringCooldown() {
        // Given: Recent scaling action within cooldown period
        autoScaler.recordScalingAction(Instant.now().minusSeconds(100)); // 100s ago
        when(metricsProvider.getAverageCpuUsage()).thenReturn(80.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should not scale due to cooldown
        assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
        assertThat(decision.getReason()).contains("Within cooldown period");
    }

    @Test
    @DisplayName("Should allow scaling after cooldown expires")
    void shouldAllowScalingAfterCooldown() {
        // Given: Scaling action outside cooldown period
        autoScaler.recordScalingAction(Instant.now().minusSeconds(400)); // 400s ago (> 300s cooldown)
        when(metricsProvider.getAverageCpuUsage()).thenReturn(80.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should allow scaling
        assertThat(decision.getAction()).isEqualTo(ScalingAction.SCALE_UP);
    }

    @Test
    @DisplayName("Should handle metrics provider errors gracefully")
    void shouldHandleMetricsErrorsGracefully() {
        // Given: Metrics provider throws exception
        when(metricsProvider.getAverageCpuUsage()).thenThrow(new RuntimeException("Metrics unavailable"));
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Should not scale and indicate error
        assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
        assertThat(decision.getReason()).contains("Metrics unavailable");
    }

    @Test
    @DisplayName("Should provide detailed scaling decision information")
    void shouldProvideDetailedDecisionInfo() {
        // Given: Scale up scenario
        when(metricsProvider.getAverageCpuUsage()).thenReturn(75.0);
        when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

        // When: Evaluate scaling decision
        ScalingDecision decision = autoScaler.evaluateScaling();

        // Then: Decision contains all relevant information
        assertThat(decision.getCurrentInstances()).isEqualTo(2);
        assertThat(decision.getTargetInstances()).isEqualTo(3);
        assertThat(decision.getCpuUsage()).isEqualTo(75.0);
        assertThat(decision.getTimestamp()).isNotNull();
        assertThat(decision.getReason()).isNotEmpty();
    }
}