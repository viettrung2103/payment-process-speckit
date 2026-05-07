package com.payment.bridge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @Column(name = "client_reference", length = 255)
    private String clientReference;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "api_response", columnDefinition = "jsonb")
    private String apiResponse;

    @Column(name = "api_status_code")
    private Integer apiStatusCode;

    @Column(name = "external_transaction_id", length = 255)
    private String externalTransactionId;

    @Column(name = "retry_count_api", nullable = false)
    private Integer retryCountApi = 0;

    @Column(name = "retry_count_db", nullable = false)
    private Integer retryCountDb = 0;

    @Column(name = "error_reason", columnDefinition = "text")
    private String errorReason;

    @Column(name = "error_details", columnDefinition = "jsonb")
    private String errorDetails;

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getApiResponse() {
        return apiResponse;
    }

    public void setApiResponse(String apiResponse) {
        this.apiResponse = apiResponse;
    }

    public Integer getApiStatusCode() {
        return apiStatusCode;
    }

    public void setApiStatusCode(Integer apiStatusCode) {
        this.apiStatusCode = apiStatusCode;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public Integer getRetryCountApi() {
        return retryCountApi;
    }

    public void setRetryCountApi(Integer retryCountApi) {
        this.retryCountApi = retryCountApi;
    }

    public Integer getRetryCountDb() {
        return retryCountDb;
    }

    public void setRetryCountDb(Integer retryCountDb) {
        this.retryCountDb = retryCountDb;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }
}
