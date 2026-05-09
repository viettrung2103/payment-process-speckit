package com.payment.mock.integration;

import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionStatus;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Payment Flow Integration Tests")
class PaymentFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Full payment flow: POST /api/v1/payments processes and persists transaction")
    void testFullPaymentFlow() {
        // Arrange
        String transactionId = "ITG-TXN-001";
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String clientRef = "CLIENT-ITG-001";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: Send payment request
        ResponseEntity<Map> paymentResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );

        // Assert: Response is OK
        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode());
        assertNotNull(paymentResponse.getBody());
        assertEquals(transactionId, paymentResponse.getBody().get("transactionId"));

        // Verify transaction persisted to H2
        Optional<Transaction> savedTxn = transactionRepository.findByTransactionId(transactionId);
        assertTrue(savedTxn.isPresent(), "Transaction should be saved to H2");

        Transaction transaction = savedTxn.get();
        assertEquals(transactionId, transaction.getTransactionId());
        assertEquals(amount, transaction.getAmount());
        assertEquals(currency, transaction.getCurrency());
        assertEquals(clientRef, transaction.getClientReference());
        assertNotNull(transaction.getCreatedAt());
        assertNotNull(transaction.getProcessedAt());
        assertTrue(
                transaction.getStatus() == TransactionStatus.COMPLETED ||
                        transaction.getStatus() == TransactionStatus.FAILED,
                "Transaction status should be terminal state (COMPLETED or FAILED)"
        );
    }

    @Test
    @DisplayName("Delay is honored in payment processing")
    void testPaymentProcessingDelayHonored() {
        // Arrange
        String transactionId = "ITG-TXN-002";
        BigDecimal amount = new BigDecimal("50.00");
        String currency = "EUR";
        String clientRef = "CLIENT-ITG-002";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: Record time and send payment request
        long startTime = System.currentTimeMillis();
        ResponseEntity<Map> paymentResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );
        long elapsedMs = System.currentTimeMillis() - startTime;

        // Assert: Response is OK
        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode());

        // Verify delay was honored (default min-delay-ms for test profile is 1ms)
        // Total elapsed should be >= some minimum (accounting for test profile delays)
        assertTrue(elapsedMs >= 1,
                String.format("Total elapsed time %dms should be at least 1ms", elapsedMs));

        // Verify transaction is in DB
        Optional<Transaction> savedTxn = transactionRepository.findByTransactionId(transactionId);
        assertTrue(savedTxn.isPresent(), "Transaction should be persisted");
    }

    @Test
    @DisplayName("GET /api/v1/payments/status/{id} retrieves same transaction")
    void testGetPaymentStatus() throws InterruptedException {
        // Arrange: Create and persist a payment
        String transactionId = "ITG-TXN-003";
        BigDecimal amount = new BigDecimal("75.00");
        String currency = "GBP";
        String clientRef = "CLIENT-ITG-003";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: Process payment
        ResponseEntity<Map> paymentResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );
        assertEquals(HttpStatus.OK, paymentResponse.getStatusCode());

        // Act: Query transaction status
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                "/api/v1/payments/status/" + transactionId,
                Map.class
        );

        // Assert: Status response matches created transaction
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        Map<String, Object> statusBody = statusResponse.getBody();
        assertNotNull(statusBody);
        assertEquals(transactionId, statusBody.get("transactionId"));
        assertEquals(amount.doubleValue(), statusBody.get("amount"));
        assertEquals(currency, statusBody.get("currency"));
        assertEquals(clientRef, statusBody.get("clientReference"));
    }

    @Test
    @DisplayName("Multiple sequential payments are independently stored and retrieved")
    void testMultiplePaymentsIndependence() {
        // Arrange & Act: Process multiple payments
        for (int i = 1; i <= 5; i++) {
            String transactionId = "ITG-TXN-SEQ-" + i;
            BigDecimal amount = new BigDecimal(100 * i);
            String currency = "USD";
            String clientRef = "CLIENT-SEQ-" + i;

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

        // Assert: All transactions exist in DB with correct amounts
        for (int i = 1; i <= 5; i++) {
            String transactionId = "ITG-TXN-SEQ-" + i;
            Optional<Transaction> txn = transactionRepository.findByTransactionId(transactionId);
            assertTrue(txn.isPresent(), "Transaction " + i + " should be persisted");
            assertEquals(0,
                    new BigDecimal(100 * i).compareTo(txn.get().getAmount()),
                    "Transaction " + i + " amount should match"
            );
        }
    }

    @Test
    @DisplayName("Response structure includes all required fields")
    void testResponseStructure() {
        // Arrange
        String transactionId = "ITG-TXN-STRUCT";
        BigDecimal amount = new BigDecimal("123.45");
        String currency = "CAD";
        String clientRef = "CLIENT-STRUCT";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );

        // Assert: Response contains required fields
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("transactionId"), "Response should contain transactionId");
        assertTrue(body.containsKey("status"), "Response should contain status");
        assertTrue(body.containsKey("processedAt"), "Response should contain processedAt");
        assertTrue(body.containsKey("responseTimeMs"), "Response should contain responseTimeMs");
        assertNotNull(body.get("responseTimeMs"), "responseTimeMs should not be null");
    }

    @Test
    @DisplayName("Duplicate transaction returns existing record")
    void testDuplicateTransactionHandling() {
        // Arrange
        String transactionId = "ITG-TXN-DUP";
        BigDecimal amount = new BigDecimal("200.00");
        String currency = "CHF";
        String clientRef = "CLIENT-DUP";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: First request
        ResponseEntity<Map> firstResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );
        assertEquals(HttpStatus.OK, firstResponse.getStatusCode());
        String firstStatus = (String) firstResponse.getBody().get("status");

        // Act: Duplicate request with same transaction ID
        ResponseEntity<Map> secondResponse = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );

        // Assert: Both responses should have same status (idempotent)
        assertEquals(HttpStatus.OK, secondResponse.getStatusCode());
        String secondStatus = (String) secondResponse.getBody().get("status");
        assertEquals(firstStatus, secondStatus, "Duplicate request should return same status");

        // Verify only one transaction in DB
        long count = transactionRepository.count();
        assertEquals(1, count, "Should only have one transaction for duplicate ID");
    }
}
