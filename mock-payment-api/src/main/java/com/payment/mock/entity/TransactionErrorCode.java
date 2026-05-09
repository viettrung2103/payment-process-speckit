package com.payment.mock.entity;

public enum TransactionErrorCode {
    VALIDATION_ERROR("VALIDATION_ERROR", 400),
    TIMEOUT("TIMEOUT", 504),
    RATE_LIMITED("RATE_LIMITED", 429),
    INTERNAL_ERROR("INTERNAL_ERROR", 500),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", 503);

    private final String code;
    private final int httpStatus;

    TransactionErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
