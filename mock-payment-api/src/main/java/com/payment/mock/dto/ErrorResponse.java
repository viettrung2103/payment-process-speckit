package com.payment.mock.dto;

public class ErrorResponse {

    private Integer code;
    private String message;
    private String errorCode;
    private String transactionId;
    private Integer retryAfter;

    public ErrorResponse() {
    }

    public ErrorResponse(Integer code, String message, String errorCode, String transactionId, Integer retryAfter) {
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
        this.transactionId = transactionId;
        this.retryAfter = retryAfter;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Integer getRetryAfter() {
        return retryAfter;
    }

    public void setRetryAfter(Integer retryAfter) {
        this.retryAfter = retryAfter;
    }
}
