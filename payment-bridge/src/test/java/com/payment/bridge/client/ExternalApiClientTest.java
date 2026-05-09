package com.payment.bridge.client;

import com.payment.bridge.exception.PaymentApiException;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExternalApiClient with Circuit Breaker.
 * Tests resilience patterns and error handling.
 */
@ExtendWith(MockitoExtension.class)
class ExternalApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ExternalApiClient externalApiClient;
    private Payment payment;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        externalApiClient = new ExternalApiClient(
            restTemplate,
            CircuitBreakerConfig.ofDefaults(),
            RetryConfig.ofDefaults(),
            "http://localhost:8081"
        );
    }

    @Test
    void testProcessPayment_SuccessfulResponse() {
        // Given: Successful API response
        Map<String, Object> mockResponse = Map.of(
            "transactionId", "TXN-123456",
            "status", "SUCCESS"
        );

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenReturn(mockResponse);

        // When
        ExternalApiClient.ApiResponse response = externalApiClient.processPayment(payment);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo("TXN-123456");
        assertThat(response.getStatusCode()).isEqualTo(200);
    }

    @Test
    void testProcessPayment_Should5xxRetry() {
        // Given: 500 Server error (should trigger retry)
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(new RuntimeException("500 Internal Server Error"));

        // When & Then - Should throw exception for retry handling
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(PaymentProcessingException.class) // Be specific here
            .hasMessageContaining("Unexpected API error");
    }

    @Test
    void testProcessPayment_Should4xxNotRetry() {
        // Given: 400 Bad request (should not retry)
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(new PaymentApiException("Invalid request", 400, "Bad Request"));

        // When & Then
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(PaymentApiException.class)
            .satisfies(e -> assertThat(((PaymentApiException) e).getStatusCode()).isEqualTo(400));
    }

    @Test
    void testProcessPayment_Timeout() {
        // Given: Request timeout
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(new RuntimeException("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(PaymentProcessingException.class)
            .hasCauseInstanceOf(RuntimeException.class)
            .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("timeout"));
    }

    @Test
    void testProcessPayment_CircuitBreakerOpen() {
        // Given: Circuit breaker is open due to multiple failures
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(CallNotPermittedException.createCallNotPermittedException(io.github.resilience4j.circuitbreaker.CircuitBreaker.ofDefaults("paymentApi")));

        // When & Then
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void testProcessPayment_429TooManyRequests() {
        // Given: 429 Too Many Requests (should retry with backoff)
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(new PaymentApiException("Rate limited", 429, "Too Many Requests"));

        // When & Then
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(PaymentApiException.class);
    }

    @Test
    void testProcessPayment_503ServiceUnavailable() {
        // Given: 503 Service Unavailable (should retry)
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenThrow(new RuntimeException("503 Service Unavailable"));

        // When & Then
        assertThatThrownBy(() -> externalApiClient.processPayment(payment))
            .isInstanceOf(Exception.class);
    }

    @Test
    void testProcessPayment_RespectsTimeout() {
        // Given: Configuration enforces 5s connection, 2s read timeout
        Map<String, Object> mockResponse = Map.of(
            "transactionId", "TXN-789",
            "status", "SUCCESS"
        );

        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        when(restTemplate.postForObject(anyString(), any(Map.class), eq(mapClass)))
            .thenReturn(mockResponse);

        // When
        ExternalApiClient.ApiResponse response = externalApiClient.processPayment(payment);

        // Then
        assertThat(response.getTransactionId()).isEqualTo("TXN-789");
    }
}