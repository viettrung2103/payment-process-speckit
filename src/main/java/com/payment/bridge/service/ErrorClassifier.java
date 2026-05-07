package com.payment.bridge.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Predicate;

@Configuration
public class ErrorClassifier {

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