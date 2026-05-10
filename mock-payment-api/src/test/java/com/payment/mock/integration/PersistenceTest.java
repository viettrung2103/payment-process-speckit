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
@DisplayName("H2 Persistence Integration Tests")
class PersistenceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Transaction persisted to H2 after payment processing")
    void testTransactionPersisted() {
        // Arrange
        String transactionId = "PERSIST-001";
        BigDecimal amount = new BigDecimal("150.00");
        String currency = "USD";
        String clientRef = "CLIENT-PERSIST";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: Process payment
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );

        // Assert: Response is OK
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Assert: Transaction exists in H2
        Optional<Transaction> savedTxn = transactionRepository.findByTransactionId(transactionId);
        assertTrue(savedTxn.isPresent(), "Transaction should be persisted to H2");

        Transaction txn = savedTxn.get();
        assertEquals(transactionId, txn.getTransactionId());
        assertEquals(amount, txn.getAmount());
        assertEquals(currency, txn.getCurrency());
        assertEquals(clientRef, txn.getClientReference());
        assertNotNull(txn.getCreatedAt(), "createdAt should be set");
        assertNotNull(txn.getProcessedAt(), "processedAt should be set");
    }

    @Test
    @DisplayName("Transaction status is persisted correctly")
    void testTransactionStatusPersisted() {
        // Arrange: Process multiple transactions and verify status persistence
        for (int i = 1; i <= 20; i++) {
            String transactionId = "STATUS-" + String.format("%02d", i);
            BigDecimal amount = new BigDecimal("100.00");
            String currency = "USD";
            String clientRef = "CLIENT-STATUS-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            // Act: Process payment
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Assert: Status in response and DB match
            String responseStatus = (String) response.getBody().get("status");
            Optional<Transaction> savedTxn = transactionRepository.findByTransactionId(transactionId);
            assertTrue(savedTxn.isPresent());

            String dbStatus = savedTxn.get().getStatus().toString();
            assertEquals(responseStatus, dbStatus,
                    "Status in response should match status in database");

            // Verify status is terminal (COMPLETED or FAILED)
            assertTrue(
                    "COMPLETED".equals(responseStatus) || "FAILED".equals(responseStatus),
                    "Status should be terminal: COMPLETED or FAILED"
            );
        }

        // Verify all transactions persisted
        long count = transactionRepository.count();
        assertEquals(20, count, "All 20 transactions should be persisted");
    }

    @Test
    @DisplayName("Transaction amounts are persisted correctly")
    void testTransactionAmountsPersisted() {
        // Arrange: Create transactions with varying amounts
        BigDecimal[] amounts = new BigDecimal[]{
                new BigDecimal("10.00"),
                new BigDecimal("50.99"),
                new BigDecimal("1000.00"),
                new BigDecimal("0.01")
        };

        // Act: Process payments with different amounts
        for (int i = 0; i < amounts.length; i++) {
            String transactionId = "AMOUNT-" + i;
            BigDecimal amount = amounts[i];
            String currency = "USD";
            String clientRef = "CLIENT-AMOUNT-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        }

        // Assert: All amounts persisted correctly
        for (int i = 0; i < amounts.length; i++) {
            Optional<Transaction> txn = transactionRepository.findByTransactionId("AMOUNT-" + i);
            assertTrue(txn.isPresent());
            assertEquals(amounts[i], txn.get().getAmount(),
                    "Amount should be persisted correctly");
        }
    }

    @Test
    @DisplayName("Currency is persisted correctly")
    void testCurrencyPersisted() {
        // Arrange: Test multiple currencies
        String[] currencies = {"USD", "EUR", "GBP", "JPY", "CAD"};

        // Act & Assert: Process payments in different currencies
        for (int i = 0; i < currencies.length; i++) {
            String transactionId = "CURRENCY-" + i;
            BigDecimal amount = new BigDecimal("100.00");
            String currency = currencies[i];
            String clientRef = "CLIENT-CURRENCY-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);

            Optional<Transaction> txn = transactionRepository.findByTransactionId(transactionId);
            assertTrue(txn.isPresent());
            assertEquals(currency, txn.get().getCurrency(),
                    "Currency " + currency + " should be persisted");
        }
    }

    @Test
    @DisplayName("Client reference is persisted")
    void testClientReferencePersisted() {
        // Arrange
        String transactionId = "CLIENTREF-001";
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String clientRef = "UNIQUE-CLIENT-REF-12345";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act
        restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);

        // Assert
        Optional<Transaction> txn = transactionRepository.findByTransactionId(transactionId);
        assertTrue(txn.isPresent());
        assertEquals(clientRef, txn.get().getClientReference(),
                "Client reference should be persisted");
    }

    @Test
    @DisplayName("Failure reasons are persisted for failed transactions")
    void testFailureReasonsPersisted() {
        // Arrange: Process transactions until we get some failures
        int numRequests = 200;
        int failureCount = 0;

        // Act: Send payments
        for (int i = 0; i < numRequests; i++) {
            String transactionId = "FAILREASON-" + System.currentTimeMillis() + "-" + i;
            BigDecimal amount = new BigDecimal("100.00");
            String currency = "USD";
            String clientRef = "CLIENT-FAILREASON-" + i;

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
                    failureCount++;

                    // Assert: Failure reason is in response
                    String failureReason = (String) response.getBody().get("failureReason");
                    assertNotNull(failureReason, "Failed transaction should have failure reason");

                    // Assert: Failure reason persisted to DB
                    Optional<Transaction> txn = transactionRepository.findByTransactionId(transactionId);
                    assertTrue(txn.isPresent());
                    assertEquals(failureReason, txn.get().getFailureReason(),
                            "Failure reason should be persisted to database");
                }
            }
        }

        // Verify we had some failures
        assertTrue(failureCount > 0, "Should have at least some failures with 10% rate");
    }

    @Test
    @DisplayName("Created and processed timestamps are set")
    void testTimestampsSet() {
        // Arrange
        String transactionId = "TIMESTAMP-001";
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String clientRef = "CLIENT-TIMESTAMP";

        Map<String, Object> paymentRequest = Map.of(
                "transactionId", transactionId,
                "amount", amount,
                "currency", currency,
                "clientReference", clientRef
        );

        // Act: Record request time
        long beforeRequest = System.currentTimeMillis();
        restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        long afterRequest = System.currentTimeMillis();

        // Assert: Timestamps are in database
        Optional<Transaction> txn = transactionRepository.findByTransactionId(transactionId);
        assertTrue(txn.isPresent());

        assertNotNull(txn.get().getCreatedAt(), "createdAt should be set");
        assertNotNull(txn.get().getProcessedAt(), "processedAt should be set");

        // Verify timestamps are reasonable
        assertTrue(
                txn.get().getCreatedAt().isBefore(txn.get().getProcessedAt()),
                "createdAt should be before processedAt"
        );
    }

    @Test
    @DisplayName("Concurrent transactions all persisted to H2")
    void testConcurrentTransactionsPersisted() throws InterruptedException {
        // Arrange: Send multiple requests in parallel
        int numThreads = 10;
        int requestsPerThread = 5;
        Thread[] threads = new Thread[numThreads];

        // Act: Process transactions concurrently
        for (int t = 0; t < numThreads; t++) {
            final int threadNum = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < requestsPerThread; i++) {
                    String transactionId = "CONCURRENT-" + threadNum + "-" + i;
                    BigDecimal amount = new BigDecimal("100.00");
                    String currency = "USD";
                    String clientRef = "CLIENT-CONCURRENT-" + threadNum + "-" + i;

                    Map<String, Object> paymentRequest = Map.of(
                            "transactionId", transactionId,
                            "amount", amount,
                            "currency", currency,
                            "clientReference", clientRef
                    );

                    restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert: All transactions persisted
        long expectedCount = (long) numThreads * requestsPerThread;
        long actualCount = transactionRepository.count();
        assertEquals(expectedCount, actualCount,
                String.format("Expected %d transactions, but found %d", expectedCount, actualCount));
    }

    @Test
    @DisplayName("H2 database survives multiple operations")
    void testDatabaseStability() {
        // Arrange: Perform multiple operations
        int numBatches = 5;
        int txnPerBatch = 20;

        // Act & Assert: Multiple batches of transactions
        for (int batch = 0; batch < numBatches; batch++) {
            for (int i = 0; i < txnPerBatch; i++) {
                String transactionId = "STABLE-" + batch + "-" + i;
                BigDecimal amount = new BigDecimal("100.00");
                String currency = "USD";
                String clientRef = "CLIENT-STABLE-" + batch + "-" + i;

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

                assertEquals(HttpStatus.OK, response.getStatusCode(),
                        "Request in batch " + batch + " should succeed");
            }

            // Verify count after each batch
            long expectedCount = (long) (batch + 1) * txnPerBatch;
            long actualCount = transactionRepository.count();
            assertEquals(expectedCount, actualCount,
                    "After batch " + batch + ", count should be " + expectedCount);
        }
    }
}
