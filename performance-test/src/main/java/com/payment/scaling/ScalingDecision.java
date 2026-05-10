package com.payment.scaling;

import java.time.Instant;

/**
 * Represents a scaling decision with detailed information
 */
public class ScalingDecision {

    private final ScalingAction action;
    private final int targetInstances;
    private final String reason;
    private final int currentInstances;
    private final double cpuUsage;
    private final Instant timestamp;

    private ScalingDecision(ScalingAction action, int targetInstances, String reason,
                           int currentInstances, double cpuUsage) {
        this.action = action;
        this.targetInstances = targetInstances;
        this.reason = reason;
        this.currentInstances = currentInstances;
        this.cpuUsage = cpuUsage;
        this.timestamp = Instant.now();
    }

    public static ScalingDecision scaleUp(int targetInstances, String reason,
                                        int currentInstances, double cpuUsage) {
        return new ScalingDecision(ScalingAction.SCALE_UP, targetInstances, reason,
                                 currentInstances, cpuUsage);
    }

    public static ScalingDecision scaleDown(int targetInstances, String reason,
                                          int currentInstances, double cpuUsage) {
        return new ScalingDecision(ScalingAction.SCALE_DOWN, targetInstances, reason,
                                 currentInstances, cpuUsage);
    }

    public static ScalingDecision noAction(String reason, int currentInstances, double cpuUsage) {
        return new ScalingDecision(ScalingAction.NO_ACTION, currentInstances, reason,
                                 currentInstances, cpuUsage);
    }

    // Getters
    public ScalingAction getAction() { return action; }
    public int getTargetInstances() { return targetInstances; }
    public String getReason() { return reason; }
    public int getCurrentInstances() { return currentInstances; }
    public double getCpuUsage() { return cpuUsage; }
    public Instant getTimestamp() { return timestamp; }
}