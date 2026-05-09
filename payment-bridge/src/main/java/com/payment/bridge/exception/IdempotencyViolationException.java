package com.payment.bridge.exception;

public class IdempotencyViolationException extends PaymentException {

    public IdempotencyViolationException(String message) {
        super(message);
    }

    public IdempotencyViolationException(String message, Throwable cause) {
        super(message, cause);
    }
}