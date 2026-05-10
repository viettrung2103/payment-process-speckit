package com.payment.mock.integration;

import com.payment.mock.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Failure Distribution Integration Tests")
class FailureDistributionTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final int NUM_REQUESTS = 500;
    private static final double EXPECTED_SUCCESS_RATE = 0.90;
    private static final double SUCCESS_TOLERANCE = 0.05; // ±5%

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Success rate approximately 90% over 500 requests")
    void testSuccessRateDistribution() {
        // Arrange: Counter for successful responses
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);

        // Act: Send 500 payment requests
        for (int i = 0; i < NUM_REQUESTS; i++) {
            String transactionId = "DIST-TXN-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = new BigDecimal("100.00");
            String currency = "USD";
            String clientRef = "CLIENT-DIST-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String status = (String) response.getBody().get("status");
                if ("COMPLETED".equals(status)) {
                    successCount.incrementAndGet();
                } else if ("FAILED".equals(status)) {
                    failureCount.incrementAndGet();
                }
            }
            totalCount.incrementAndGet();
        }

        // Assert: Distribution matches expected (90% success ±5%)
        double actualSuccessRate = (double) successCount.get() / totalCount.get();
        double lowerBound = EXPECTED_SUCCESS_RATE - SUCCESS_TOLERANCE;
        double upperBound = EXPECTED_SUCCESS_RATE + SUCCESS_TOLERANCE;

        assertTrue(
                actualSuccessRate >= lowerBound && actualSuccessRate <= upperBound,
                String.format("Success rate %.2f%% should be between %.2f%% and %.2f%%",
                        actualSuccessRate * 100, lowerBound * 100, upperBound * 100)
        );

        // Verify failure count is approximately 10% ±5%
        double actualFailureRate = (double) failureCount.get() / totalCount.get();
        double expectedFailureRate = 1.0 - EXPECTED_SUCCESS_RATE;
        double failureToleranceBound = SUCCESS_TOLERANCE;
        assertTrue(
                actualFailureRate >= (expectedFailureRate - failureToleranceBound) &&
                        actualFailureRate <= (expectedFailureRate + failureToleranceBound),
                String.format("Failure rate %.2f%% should be approximately 10%% ±5%%", actualFailureRate * 100)
        );
    }

    @Test
    @DisplayName("All transactions persisted to H2 database")
    void testAllTransactionsPersisted() {
        // Arrange: Send N payment requests
        int numRequests = 100;

        // Act: Process payments
        for (int i = 0; i < numRequests; i++) {
            String transactionId = "PERSIST-TXN-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = new BigDecimal(50 + (i % 50));
            String currency = "USD";
            String clientRef = "CLIENT-PERSIST-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        // Assert: All transactions exist in database
        long dbCount = transactionRepository.count();
        assertEquals(numRequests, dbCount,
                String.format("Expected %d transactions in database, but found %d",
                        numRequests, dbCount));
    }

    @Test
    @DisplayName("Failed transactions have failure reason populated")
    void testFailedTransactionsHaveReason() {
        // Arrange: Send enough requests to expect some failures
        int numRequests = 200;
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act: Process payments
        for (int i = 0; i < numRequests; i++) {
            String transactionId = "FAIL-REASON-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = new BigDecimal("100.00");
            String currency = "USD";
            String clientRef = "CLIENT-FAIL-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String status = (String) response.getBody().get("status");
                if ("FAILED".equals(status)) {
                    failureCount.incrementAndGet();
                }
            }
        }

        // Assert: Verify at least some failures occurred and check reasons
        assertTrue(failureCount.get() > 0, "Should have at least some failures with ~10% rate");

        // Verify failed transactions have failure reasons
        transactionRepository.findAll().stream()
                .filter(t -> "FAILED".equals(t.getStatus().toString()))
                .forEach(t -> assertNotNull(t.getFailureReason(),
                        "Failed transaction " + t.getTransactionId() + " should have failure reason"));
    }

    @Test
    @DisplayName("Realistic distribution over extended test")
    void testRealisticDistribution() {
        // Arrange: Send 300 requests for statistical validation
        int numRequests = 300;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act: Process payments
        for (int i = 0; i < numRequests; i++) {
            String transactionId = "REALISTIC-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = new BigDecimal("100.00");
            String currency = "USD";
            String clientRef = "CLIENT-REALISTIC-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String status = (String) response.getBody().get("status");
                if ("COMPLETED".equals(status)) {
                    successCount.incrementAndGet();
                } else if ("FAILED".equals(status)) {
                    failureCount.incrementAndGet();
                }
            }
        }

        // Assert: Check distributions are reasonable
        double successRate = (double) successCount.get() / numRequests;
        double failureRate = (double) failureCount.get() / numRequests;

        // Both should be reasonable (not 100% success or 100% failure)
        assertTrue(successRate > 0.5, "Success rate should be > 50%");
        assertTrue(failureRate > 0.01, "Failure rate should be > 1%");
        assertTrue(failureRate < 0.5, "Failure rate should be < 50%");

        // Total should be approximately 100%
        assertEquals(1.0, successRate + failureRate, 0.01,
                "Success + failure rate should equal 100% ±1%");
    }
}
