package com.payment.scaling;

import java.time.Instant;

/**
 * Auto-scaling logic for payment-bridge instances
 * Implements CPU-based scaling with cooldown periods
 */
public class AutoScaler {

    private final MetricsProvider metricsProvider;
    private final InstanceManager instanceManager;
    private final int minInstances;
    private final int maxInstances;
    private final double scaleUpThreshold;
    private final double scaleDownThreshold;
    private final int cooldownSeconds;

    private Instant lastScalingAction = Instant.EPOCH;

    public AutoScaler(MetricsProvider metricsProvider, InstanceManager instanceManager,
                     int minInstances, int maxInstances, double scaleUpThreshold,
                     double scaleDownThreshold, int cooldownSeconds) {
        this.metricsProvider = metricsProvider;
        this.instanceManager = instanceManager;
        this.minInstances = minInstances;
        this.maxInstances = maxInstances;
        this.scaleUpThreshold = scaleUpThreshold;
        this.scaleDownThreshold = scaleDownThreshold;
        this.cooldownSeconds = cooldownSeconds;
    }

    public ScalingDecision evaluateScaling() {
        try {
            double cpuUsage = metricsProvider.getAverageCpuUsage();
            int currentInstances = instanceManager.getCurrentInstanceCount();

            // Check cooldown period
            if (Instant.now().isBefore(lastScalingAction.plusSeconds(cooldownSeconds))) {
                return ScalingDecision.noAction("Within cooldown period", currentInstances, cpuUsage);
            }

            // Evaluate scaling conditions
            if (cpuUsage > scaleUpThreshold && currentInstances < maxInstances) {
                return ScalingDecision.scaleUp(currentInstances + 1,
                    String.format("CPU usage %.1f%% > %.1f%%", cpuUsage, scaleUpThreshold),
                    currentInstances, cpuUsage);
            }

            if (cpuUsage < scaleDownThreshold && currentInstances > minInstances) {
                return ScalingDecision.scaleDown(currentInstances - 1,
                    String.format("CPU usage %.1f%% < %.1f%%", cpuUsage, scaleDownThreshold),
                    currentInstances, cpuUsage);
            }

            if (cpuUsage > scaleUpThreshold && currentInstances >= maxInstances) {
                return ScalingDecision.noAction("Already at maximum instances (" + maxInstances + ")",
                    currentInstances, cpuUsage);
            }

            if (cpuUsage < scaleDownThreshold && currentInstances <= minInstances) {
                return ScalingDecision.noAction("Already at minimum instances (" + minInstances + ")",
                    currentInstances, cpuUsage);
            }

            return ScalingDecision.noAction(
                String.format("CPU usage %.1f%% within optimal range", cpuUsage),
                currentInstances, cpuUsage);

        } catch (Exception e) {
            return ScalingDecision.noAction("Metrics unavailable: " + e.getMessage(), 0, 0.0);
        }
    }

    public void recordScalingAction(Instant timestamp) {
        this.lastScalingAction = timestamp;
    }

    public void recordScalingAction() {
        this.lastScalingAction = Instant.now();
    }
}