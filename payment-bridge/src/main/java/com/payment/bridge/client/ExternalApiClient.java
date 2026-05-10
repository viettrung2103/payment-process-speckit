package com.payment.bridge.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.payment.bridge.exception.PaymentApiException;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.Payment;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ExternalApiClient {

    private static final Logger logger = LoggerFactory.getLogger(ExternalApiClient.class);

    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final String baseUrl;

    public ExternalApiClient(RestTemplate restTemplate,
                             CircuitBreakerConfig circuitBreakerConfig,
                             RetryConfig retryConfig,
                             @Value("${payment.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.circuitBreaker = CircuitBreaker.of("paymentApi", circuitBreakerConfig);
        this.retry = Retry.of("paymentApi", retryConfig);
        this.baseUrl = baseUrl;
    }

    public ApiResponse processPayment(Payment payment) {
        Supplier<ApiResponse> supplier = () -> callExternalApi(payment);

        Supplier<ApiResponse> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        decorated = Retry.decorateSupplier(retry, decorated);

        try {
            return decorated.get();
        } catch (PaymentApiException | CallNotPermittedException | PaymentProcessingException e) {
            // Let our known business/resilience exceptions through
            throw e;
        } catch (Exception e) {
            // Wrap unexpected checked exceptions or generic runtime errors
            logger.error("External API request failed for payment {}", payment.getPaymentId(), e);
            throw new PaymentProcessingException("Unexpected API error", e);
        }
    }

    public ApiResponse getPaymentStatus(UUID paymentId) {
        String endpoint = String.format("%s/api/v1/payments/status/%s", baseUrl, paymentId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = restTemplate.getForObject(endpoint, Map.class);
            if (responseMap == null) {
                throw new PaymentProcessingException("External API returned null status response");
            }
            return mapToApiResponse(responseMap);
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            if (statusCode >= 400 && statusCode < 500) {
                throw new PaymentApiException("External API status check failed", statusCode, body, e);
            }
            throw new RuntimeException("External API status endpoint error", e);
        }
    }

    private ApiResponse mapToApiResponse(Map<String, Object> responseMap) {
        ApiResponse response = new ApiResponse();
        response.setTransactionId((String) responseMap.get("transactionId"));
        response.setStatus((String) responseMap.get("status"));
        response.setStatusCode(responseMap.containsKey("code") ? (Integer) responseMap.get("code") : 200);
        response.setBody(responseMap.toString());
        response.setMessage((String) responseMap.get("message"));
        return response;
    }

    private ApiResponse callExternalApi(Payment payment) {
        String endpoint = String.format("%s/api/v1/payments", baseUrl);
        Map<String, Object> request = new HashMap<>();
        request.put("transactionId", payment.getPaymentId());
        request.put("amount", payment.getAmount());
        request.put("currency", payment.getCurrency());
        request.put("clientReference", payment.getClientReference());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = restTemplate.postForObject(endpoint, request, Map.class);
            if (responseMap == null) {
                throw new PaymentProcessingException("External API returned null response");
            }

            ApiResponse response = mapToApiResponse(responseMap);

            // Check if the payment failed
            String status = response.getStatus();
            if ("FAILED".equals(status)) {
                String failureReason = (String) responseMap.get("failureReason");
                throw new PaymentApiException("Payment processing failed: " + failureReason, 200, responseMap.toString());
            }
            if (!"COMPLETED".equals(status) && !"SUCCESS".equals(status)) {
                throw new PaymentProcessingException("External payment not completed yet: " + status);
            }

            return response;
        } catch (HttpStatusCodeException e) {
            int statusCode = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            if (statusCode >= 400 && statusCode < 500) {
                throw new PaymentApiException("External API client error", statusCode, body, e);
            }
            throw new RuntimeException("External API server error", e);
        }
    }

    public static class ApiResponse {

        private String transactionId;
        private String status;
        private int statusCode;
        private String body;
        private String message;

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "ApiResponse{" +
                    "transactionId='" + transactionId + '\'' +
                    ", status='" + status + '\'' +
                    ", statusCode=" + statusCode +
                    ", message='" + message + '\'' +
                    ", body='" + body + '\'' +
                    '}';
        }
    }
}
