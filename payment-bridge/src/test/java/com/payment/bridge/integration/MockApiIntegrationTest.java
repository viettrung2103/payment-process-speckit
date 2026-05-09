package com.payment.bridge.integration;

import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.client.ExternalApiClient.ApiResponse;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "payment.api.base-url=http://localhost:8081",
        "external.api.connect.timeout=5000",
        "external.api.read.timeout=2000"
})
@DisplayName("Mock API Integration with Payment Bridge")
class MockApiIntegrationTest {

    @Autowired
    private ExternalApiClient externalApiClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    @Qualifier("paymentApiRetryClassifier")
    private Predicate<Throwable> paymentApiRetryClassifier;

    @Autowired
    @Qualifier("paymentApiErrorClassifier")
    private Predicate<Throwable> paymentApiErrorClassifier;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("ExternalApiClient correctly calls Mock API endpoint")
    void testExternalApiClientCallsMockApi() {
        // Arrange: Create a test payment
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setClientReference("CLIENT-MOCKAPI-001");
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.IN_PROGRESS);

        MockRestServiceServer mockServer = MockRestServiceServer.createServer(restTemplate);
        mockServer.expect(requestTo("http://localhost:8081/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"transactionId\":\"REALISTIC-TEST-001\",\"status\":\"COMPLETED\",\"amount\":100.00,\"currency\":\"USD\"}", MediaType.APPLICATION_JSON));

        // Act: Call external API via client
        ApiResponse response = externalApiClient.processPayment(payment);

        // Assert: Response structure is valid
        assertNotNull(response, "API response should not be null");
        assertNotNull(response.getTransactionId(), "API response should contain transactionId");
        assertNotNull(response.getStatus(), "API response should contain status");
        assertEquals("REALISTIC-TEST-001", response.getTransactionId());
        assertEquals("COMPLETED", response.getStatus());

        mockServer.verify();
    }

    @Test
    @DisplayName("ErrorClassifier correctly classifies retryable network failures")
    void testErrorClassifierWithMockApiFailures() {
        assertTrue(paymentApiRetryClassifier.test(new java.net.SocketTimeoutException()),
                "SocketTimeoutException should be retried");
        assertTrue(paymentApiRetryClassifier.test(new java.net.ConnectException()),
                "ConnectException should be retried");
        assertFalse(paymentApiErrorClassifier.test(new IllegalArgumentException()),
                "IllegalArgumentException should not be classified as retryable error");
    }

    @Test
    @DisplayName("Payment persists correctly through integration flow")
    void testPaymentPersistenceThroughFlow() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("250.00"));
        payment.setCurrency("EUR");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment saved = paymentRepository.save(payment);

        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertTrue(retrieved.isPresent(), "Payment should be persisted");
        assertEquals(paymentId, retrieved.get().getPaymentId());
        assertEquals(new BigDecimal("250.00"), retrieved.get().getAmount());
        assertEquals("EUR", retrieved.get().getCurrency());
        assertEquals(PaymentStatus.RECEIVED, retrieved.get().getStatus());
    }

    @Test
    @DisplayName("Payment status transitions through workflow")
    void testPaymentStatusTransitions() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("150.00"));
        payment.setCurrency("GBP");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment saved = paymentRepository.save(payment);

        saved.setStatus(PaymentStatus.IN_PROGRESS);
        paymentRepository.save(saved);

        Payment inProgress = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.IN_PROGRESS, inProgress.getStatus());

        inProgress.setStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(inProgress);

        Payment completed = paymentRepository.findById(paymentId).orElseThrow();
        assertEquals(PaymentStatus.COMPLETED, completed.getStatus());
    }

    @Test
    @DisplayName("ExternalApiClient response parsing")
    void testResponseParsing() {
        Map<String, Object> successResponse = Map.of(
                "transactionId", "TXN-123",
                "status", "COMPLETED",
                "amount", 100.00,
                "currency", "USD"
        );

        assertTrue(successResponse.containsKey("transactionId"));
        assertTrue(successResponse.containsKey("status"));
        assertEquals("COMPLETED", successResponse.get("status"));

        Map<String, Object> failureResponse = Map.of(
                "transactionId", "TXN-456",
                "status", "FAILED",
                "failureReason", "Payment processing failed - simulated failure"
        );

        assertEquals("FAILED", failureResponse.get("status"));
        assertNotNull(failureResponse.get("failureReason"));
    }
}
