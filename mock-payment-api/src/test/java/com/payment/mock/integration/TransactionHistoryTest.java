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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Transaction History Integration Tests")
class TransactionHistoryTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/v1/transactions returns paginated history")
    void testGetTransactionHistoryPaginated() {
        // Arrange: Create 50 transactions
        int numTransactions = 50;
        for (int i = 1; i <= numTransactions; i++) {
            String transactionId = "HISTORY-" + String.format("%03d", i);
            BigDecimal amount = new BigDecimal(100 + i);
            String currency = "USD";
            String clientRef = "CLIENT-HISTORY-" + i;

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

        // Act: Query history with limit=10
        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0&limit=10",
                Map.class
        );

        // Assert: Response contains pagination info
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> body = historyResponse.getBody();
        assertNotNull(body);

        List<?> transactions = (List<?>) body.get("transactions");
        assertEquals(10, transactions.size(), "Should return limit=10 transactions");
        assertEquals(10, body.get("limit"), "Limit should be 10");
        assertEquals(0, body.get("offset"), "Offset should be 0");
        assertEquals(50L, ((Number) body.get("totalCount")).longValue(), "Total count should be 50");
    }

    @Test
    @DisplayName("Pagination offset works correctly")
    void testPaginationOffset() {
        // Arrange: Create 30 transactions
        int numTransactions = 30;
        for (int i = 1; i <= numTransactions; i++) {
            String transactionId = "OFFSET-" + String.format("%03d", i);
            BigDecimal amount = new BigDecimal(100.00);
            String currency = "USD";
            String clientRef = "CLIENT-OFFSET-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        }

        // Act: Get first page (offset=0, limit=10)
        ResponseEntity<Map> firstPageResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0&limit=10",
                Map.class
        );

        // Act: Get second page (offset=10, limit=10)
        ResponseEntity<Map> secondPageResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=10&limit=10",
                Map.class
        );

        // Assert: Both pages exist and are different
        assertEquals(HttpStatus.OK, firstPageResponse.getStatusCode());
        assertEquals(HttpStatus.OK, secondPageResponse.getStatusCode());

        List<?> firstPageTxns = (List<?>) firstPageResponse.getBody().get("transactions");
        List<?> secondPageTxns = (List<?>) secondPageResponse.getBody().get("transactions");

        assertEquals(10, firstPageTxns.size());
        assertEquals(10, secondPageTxns.size());

        // Verify pages are different
        assertNotEquals(firstPageTxns.get(0), secondPageTxns.get(0),
                "First transaction on page 1 should differ from page 2");
    }

    @Test
    @DisplayName("Transactions sorted by createdAt DESC")
    void testTransactionsSortedByCreatedAtDesc() {
        // Arrange: Create transactions with small delay between each
        for (int i = 1; i <= 5; i++) {
            String transactionId = "SORT-" + i;
            BigDecimal amount = new BigDecimal(100.00);
            String currency = "USD";
            String clientRef = "CLIENT-SORT-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);

            try {
                Thread.sleep(10); // Small delay to ensure different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Act: Get all transactions
        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0&limit=100",
                Map.class
        );

        // Assert: Transactions are sorted DESC by createdAt (most recent first)
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        List<?> transactions = (List<?>) historyResponse.getBody().get("transactions");
        assertEquals(5, transactions.size());

        // Verify first transaction is most recent (SORT-5)
        Map<String, Object> firstTxn = (Map<String, Object>) transactions.get(0);
        assertTrue(
                firstTxn.get("transactionId").toString().startsWith("SORT-"),
                "First transaction should be most recently created"
        );
    }

    @Test
    @DisplayName("Max limit is enforced (500)")
    void testMaxLimitEnforced() {
        // Arrange: Create 100 transactions
        for (int i = 1; i <= 100; i++) {
            String transactionId = "MAXLIMIT-" + String.format("%03d", i);
            BigDecimal amount = new BigDecimal(100.00);
            String currency = "USD";
            String clientRef = "CLIENT-MAXLIMIT-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        }

        // Act: Request with limit > 500 (should be capped)
        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0&limit=1000",
                Map.class
        );

        // Assert: Limit is capped at 500
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> body = historyResponse.getBody();
        assertEquals(500, body.get("limit"),
                "Limit should be capped at 500 when requesting > 500");
    }

    @Test
    @DisplayName("Default limit is 100")
    void testDefaultLimitIs100() {
        // Arrange: Create 150 transactions
        for (int i = 1; i <= 150; i++) {
            String transactionId = "DEFAULT-" + String.format("%03d", i);
            BigDecimal amount = new BigDecimal(100.00);
            String currency = "USD";
            String clientRef = "CLIENT-DEFAULT-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        }

        // Act: Query without limit parameter
        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0",
                Map.class
        );

        // Assert: Default limit is 100
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> body = historyResponse.getBody();
        assertEquals(100, body.get("limit"),
                "Default limit should be 100");
        List<?> transactions = (List<?>) body.get("transactions");
        assertEquals(100, transactions.size(),
                "Should return 100 transactions by default");
    }

    @Test
    @DisplayName("Total count reflects all transactions")
    void testTotalCountReflectsAll() {
        // Arrange: Create 75 transactions
        int numTransactions = 75;
        for (int i = 1; i <= numTransactions; i++) {
            String transactionId = "TOTAL-" + String.format("%03d", i);
            BigDecimal amount = new BigDecimal(100.00);
            String currency = "USD";
            String clientRef = "CLIENT-TOTAL-" + i;

            Map<String, Object> paymentRequest = Map.of(
                    "transactionId", transactionId,
                    "amount", amount,
                    "currency", currency,
                    "clientReference", clientRef
            );

            restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
        }

        // Act: Query first page only
        ResponseEntity<Map> historyResponse = restTemplate.getForEntity(
                "/api/v1/transactions?offset=0&limit=20",
                Map.class
        );

        // Assert: Total count is 75 even though only 20 returned
        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> body = historyResponse.getBody();
        assertEquals(75L, ((Number) body.get("totalCount")).longValue(),
                "Total count should reflect all 75 transactions");
        List<?> transactions = (List<?>) body.get("transactions");
        assertEquals(20, transactions.size(),
                "But page should only contain 20 results");
    }
}
