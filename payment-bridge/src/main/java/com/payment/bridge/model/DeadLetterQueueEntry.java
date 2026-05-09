package com.payment.bridge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_queue")
public class DeadLetterQueueEntry {

    @Id
    @Column(name = "dlq_id", nullable = false, updatable = false)
    private UUID dlqId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "failed_action", nullable = false, length = 50)
    private String failedAction;

    @Column(name = "failure_reason", nullable = false, columnDefinition = "text")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_context", nullable = false, columnDefinition = "jsonb")
    private String paymentContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "api_response", columnDefinition = "jsonb")
    private String apiResponse;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_history", nullable = false, columnDefinition = "jsonb")
    private String retryHistory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getDlqId() {
        return dlqId;
    }

    public void setDlqId(UUID dlqId) {
        this.dlqId = dlqId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getFailedAction() {
        return failedAction;
    }

    public void setFailedAction(String failedAction) {
        this.failedAction = failedAction;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getPaymentContext() {
        return paymentContext;
    }

    public void setPaymentContext(String paymentContext) {
        this.paymentContext = paymentContext;
    }

    public String getApiResponse() {
        return apiResponse;
    }

    public void setApiResponse(String apiResponse) {
        this.apiResponse = apiResponse;
    }

    public String getRetryHistory() {
        return retryHistory;
    }

    public void setRetryHistory(String retryHistory) {
        this.retryHistory = retryHistory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}