package com.payment.bridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public class PaymentResponse {

    private UUID paymentId;
    private String status;
    private String message;
    private String estimatedProcessingTime;
    private Map<String, Map<String, String>> _links;

    public PaymentResponse() {
    }

    public PaymentResponse(UUID paymentId, String status, String message) {
        this.paymentId = paymentId;
        this.status = status;
        this.message = message;
        this.estimatedProcessingTime = Duration.ofSeconds(30).toString();
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getEstimatedProcessingTime() {
        return estimatedProcessingTime;
    }

    public void setEstimatedProcessingTime(String estimatedProcessingTime) {
        this.estimatedProcessingTime = estimatedProcessingTime;
    }

    @JsonProperty("_links")
    public Map<String, Map<String, String>> getLinks() {
        return _links;
    }

    public void setLinks(Map<String, Map<String, String>> _links) {
        this._links = _links;
    }
}
