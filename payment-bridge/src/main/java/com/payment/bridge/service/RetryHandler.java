package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RetryHandler {

    private static final Logger logger = LoggerFactory.getLogger(RetryHandler.class);

    private final PaymentPublisher paymentPublisher;

    public RetryHandler(PaymentPublisher paymentPublisher) {
        this.paymentPublisher = paymentPublisher;
    }

    /**
     * Calculates the exponential backoff delay for a retry attempt.
     * Formula: delay = (1.5^attempt - 1) seconds
     *
     * @param attemptNumber The retry attempt number (1-based)
     * @return delay in milliseconds
     */
    public long calculateBackoffDelay(int attemptNumber) {
        if (attemptNumber <= 0) {
            return 0;
        }

        // Base 1.5 exponential backoff: (1.5^attempt - 1) seconds
        double delaySeconds = Math.pow(1.5, attemptNumber) - 1;
        long delayMillis = (long) (delaySeconds * 1000);

        // Cap at reasonable maximum (5 minutes)
        return Math.min(delayMillis, 5 * 60 * 1000);
    }

    /**
     * Handles retry logic for a failed payment task.
     * Increments retry count and republishes to backoff queue with TTL.
     *
     * @param task The failed message queue task
     * @return true if retry was scheduled, false if max retries exceeded
     */
    public boolean scheduleRetry(MessageQueueTask task) {
        int currentAttempt = task.getRetryAttempt();
        int nextAttempt = currentAttempt + 1;

        if (nextAttempt > 5) {
            logger.warn("Payment {} exceeded maximum retry attempts (5), sending to DLQ", task.getPaymentId());
            return false;
        }

        long delayMillis = calculateBackoffDelay(nextAttempt);
        task.setRetryAttempt(nextAttempt);

        logger.info("Scheduling retry {} for payment {} with {}ms delay",
                   nextAttempt, task.getPaymentId(), delayMillis);

        // Publish to backoff queue with TTL
        paymentPublisher.publishPaymentTaskWithDelay(task, delayMillis);

        return true;
    }

    /**
     * Checks if a task has exceeded the maximum retry attempts.
     *
     * @param task The message queue task
     * @return true if max retries exceeded
     */
    public boolean hasExceededMaxRetries(MessageQueueTask task) {
        return task.getRetryAttempt() >= 5;
    }
}