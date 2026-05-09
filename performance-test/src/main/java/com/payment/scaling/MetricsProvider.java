package com.payment.scaling;

/**
 * Interface for providing system metrics for auto-scaling decisions
 */
public interface MetricsProvider {

    /**
     * Get the average CPU usage across all instances
     * @return CPU usage as percentage (0.0 - 100.0)
     */
    double getAverageCpuUsage();

    /**
     * Get CPU usage for a specific instance
     * @param instanceId the instance identifier
     * @return CPU usage as percentage (0.0 - 100.0)
     */
    double getCpuUsage(String instanceId);
}