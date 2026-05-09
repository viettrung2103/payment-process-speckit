package com.payment.scaling;

import java.util.List;

/**
 * Interface for managing payment-bridge instances
 */
public interface InstanceManager {

    /**
     * Get the current number of running instances
     * @return number of instances
     */
    int getCurrentInstanceCount();

    /**
     * Scale to the specified number of instances
     * @param targetCount the desired number of instances
     * @return true if scaling was successful
     */
    boolean scaleTo(int targetCount);

    /**
     * Get list of active instance identifiers
     * @return list of instance IDs
     */
    List<String> getActiveInstances();

    /**
     * Check if an instance is healthy
     * @param instanceId the instance identifier
     * @return true if instance is healthy
     */
    boolean isHealthy(String instanceId);
}