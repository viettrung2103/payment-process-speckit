package com.payment.bridge.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.hibernate.StaleObjectStateException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.util.function.Predicate;

@Configuration
public class ErrorClassifier {

    public enum ErrorAction {
        RETRY, DLQ
    }

    /**
     * Classifies an HTTP status code to determine if the error should be retried or sent to DLQ.
     *
     * @param statusCode HTTP status code
     * @return RETRY for retryable errors, DLQ for non-retryable errors
     */
    public ErrorAction classify(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            // 4xx errors are client errors - send to DLQ immediately
            return ErrorAction.DLQ;
        } else if (statusCode >= 500 && statusCode < 600) {
            // 5xx errors are server errors - retry except 501 (Not Implemented)
            if (statusCode == 501) {
                return ErrorAction.DLQ;
            } else {
                return ErrorAction.RETRY;
            }
        } else {
            // Unexpected status codes - treat as retryable
            return ErrorAction.RETRY;
        }
    }

    /**
     * Classifies an exception to determine if it should be retried or sent to DLQ.
     * Used for network-level errors that don't have HTTP status codes.
     *
     * @param throwable The exception
     * @return RETRY for retryable errors, DLQ for non-retryable errors
     */
    public ErrorAction classify(Throwable throwable) {
        // Handle PaymentApiException with status codes
        if (throwable instanceof com.payment.bridge.exception.PaymentApiException) {
            com.payment.bridge.exception.PaymentApiException apiException =
                (com.payment.bridge.exception.PaymentApiException) throwable;
            return classify(apiException.getStatusCode());
        }

        // Treat transient network and concurrency exceptions as retryable.
        if (isRetryableException(throwable)) {
            return ErrorAction.RETRY;
        }

        // Other exceptions (like runtime exceptions from circuit breaker, etc.) should go to DLQ
        return ErrorAction.DLQ;
    }

    private boolean isRetryableException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException ||
                current instanceof java.net.ConnectException ||
                current instanceof java.io.IOException ||
                current instanceof javax.net.ssl.SSLException ||
                current instanceof OptimisticLockingFailureException ||
                current instanceof ObjectOptimisticLockingFailureException ||
                current instanceof OptimisticLockException ||
                current instanceof StaleObjectStateException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Predicate to classify exceptions for circuit breaker.
     * Returns true for exceptions that should trigger circuit breaker.
     */
    @Bean
    public Predicate<Throwable> paymentApiErrorClassifier() {
        return throwable -> {
            // Circuit breaker should open for:
            // - Network timeouts
            // - 5xx server errors
            // - Connection refused
            // - SSL/TLS errors
            // But NOT for:
            // - 4xx client errors (invalid request)
            // - Authentication failures
            // - Rate limiting (429)

            if (throwable instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (throwable instanceof java.net.ConnectException) {
                return true;
            }
            if (throwable instanceof java.io.IOException) {
                return true;
            }
            if (throwable instanceof javax.net.ssl.SSLException) {
                return true;
            }

            // For HTTP status codes, we need to check the exception type
            // This would be handled by the calling service based on response status

            return false;
        };
    }

    /**
     * Predicate to classify exceptions for retry.
     * Returns true for exceptions that should trigger retry.
     */
    @Bean
    public Predicate<Throwable> paymentApiRetryClassifier() {
        return throwable -> {
            // Retry for:
            // - Network timeouts
            // - Connection issues
            // - 5xx server errors
            // - Rate limiting (429)
            // Do NOT retry for:
            // - 4xx client errors (invalid request)
            // - Authentication failures

            if (throwable instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (throwable instanceof java.net.ConnectException) {
                return true;
            }
            if (throwable instanceof java.io.IOException) {
                return true;
            }
            if (throwable instanceof javax.net.ssl.SSLException) {
                return true;
            }

            // HTTP status codes handled by service layer

            return false;
        };
    }
}