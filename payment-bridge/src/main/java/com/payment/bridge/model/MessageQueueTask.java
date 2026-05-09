package com.payment.bridge.model;

import java.time.Instant;
import java.util.UUID;

public class MessageQueueTask {

    private UUID paymentId;
    private String action;
    private int retryAttempt;
    private Instant enqueuedAt;
    private int dequeueCount;

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getRetryAttempt() {
        return retryAttempt;
    }

    public void setRetryAttempt(int retryAttempt) {
        this.retryAttempt = retryAttempt;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public void setEnqueuedAt(Instant enqueuedAt) {
        this.enqueuedAt = enqueuedAt;
    }

    public int getDequeueCount() {
        return dequeueCount;
    }

    public void setDequeueCount(int dequeueCount) {
        this.dequeueCount = dequeueCount;
    }
}
