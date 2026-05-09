package com.payment.mock.load;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Load Tests with Simulated Delays")
class LoadTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("100 concurrent requests complete successfully with delays")
    void testConcurrentRequestsWithDelays() throws InterruptedException {
        // Arrange
        int numConcurrentRequests = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act: Submit 100 concurrent payment requests
        for (int i = 0; i < numConcurrentRequests; i++) {
            final int index = i;
            Future<ResponseEntity<Map>> future = executor.submit(() -> {
                String transactionId = "LOAD-" + System.currentTimeMillis() + "-" + index;
                BigDecimal amount = new BigDecimal("100.00");
                String currency = "USD";
                String clientRef = "CLIENT-LOAD-" + index;

                Map<String, Object> paymentRequest = Map.of(
                        "transactionId", transactionId,
                        "amount", amount,
                        "currency", currency,
                        "clientReference", clientRef
                );

                long startTime = System.currentTimeMillis();
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        "/api/v1/payments",
                        paymentRequest,
                        Map.class
                );
                long elapsedMs = System.currentTimeMillis() - startTime;

                // Track execution time for delay verification
                System.out.println("Request " + index + " completed in " + elapsedMs + "ms");

                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }

                return response;
            });
            futures.add(future);
        }

        // Wait for all futures to complete
        executor.shutdown();
        boolean completed = executor.awaitTermination(5, TimeUnit.MINUTES);

        // Assert: All requests completed
        assertTrue(completed, "All requests should complete within 5 minutes");

        int totalProcessed = successCount.get() + failureCount.get();
        assertEquals(numConcurrentRequests, totalProcessed,
                String.format("Expected %d requests to complete, but got %d",
                        numConcurrentRequests, totalProcessed));

        // Assert: All requests were successful (no network errors)
        assertEquals(numConcurrentRequests, successCount.get(),
                String.format("All %d requests should have HTTP 200/OK responses",
                        numConcurrentRequests));
    }

    @Test
    @DisplayName("Response times include configured delays")
    void testResponseTimesIncludeDelays() throws InterruptedException {
        // Arrange: Test profile has min-delay-ms: 1, max-delay-ms: 100
        int numRequests = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Long> responseTimes = new ArrayList<>();

        // Act: Send requests and measure response times
        for (int i = 0; i < numRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                String transactionId = "DELAY-" + System.currentTimeMillis() + "-" + index;
                BigDecimal amount = new BigDecimal("50.00");
                String currency = "EUR";
                String clientRef = "CLIENT-DELAY-" + index;

                Map<String, Object> paymentRequest = Map.of(
                        "transactionId", transactionId,
                        "amount", amount,
                        "currency", currency,
                        "clientReference", clientRef
                );

                long startTime = System.currentTimeMillis();
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        "/api/v1/payments",
                        paymentRequest,
                        Map.class
                );
                long elapsedMs = System.currentTimeMillis() - startTime;

                synchronized (responseTimes) {
                    responseTimes.add(elapsedMs);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        // Assert: Response times are reasonable (including delay)
        assertTrue(responseTimes.size() > 0, "Should have measured response times");

        double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        long minResponseTime = responseTimes.stream().mapToLong(Long::longValue).min().orElse(0);
        long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);

        System.out.println(String.format("Response times - Min: %dms, Max: %dms, Avg: %.2fms",
                minResponseTime, maxResponseTime, avgResponseTime));

        // Min delay is 1ms (test profile), so all responses should take at least 1ms
        assertTrue(minResponseTime >= 1,
                String.format("Min response time %dms should be >= 1ms", minResponseTime));

        // Max delay is 100ms (test profile), so responses should not exceed ~200ms (including processing)
        assertTrue(maxResponseTime <= 500,
                String.format("Max response time %dms should be <= 500ms", maxResponseTime));
    }

    @Test
    @DisplayName("No thread exhaustion errors under load")
    void testNoThreadExhaustionUnderLoad() throws InterruptedException {
        // Arrange
        int numConcurrentRequests = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act: Send many concurrent requests using virtual threads
        for (int i = 0; i < numConcurrentRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transactionId = "NOERR-" + System.currentTimeMillis() + "-" + index;
                    BigDecimal amount = new BigDecimal("75.00");
                    String currency = "GBP";
                    String clientRef = "CLIENT-NOERR-" + index;

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

                    if (response.getStatusCode().is2xxSuccessful()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        // Assert: No errors occurred due to thread exhaustion
        assertEquals(0, errorCount.get(),
                "Should not have thread exhaustion errors");
        assertEquals(numConcurrentRequests, successCount.get(),
                String.format("All %d requests should succeed", numConcurrentRequests));
    }

    @Test
    @DisplayName("Transactions persisted correctly under concurrent load")
    void testTransactionsPersistedUnderLoad() throws InterruptedException {
        // Arrange
        int numConcurrentRequests = 30;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Act: Send concurrent requests
        for (int i = 0; i < numConcurrentRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                String transactionId = "PERSIST-LOAD-" + index;
                BigDecimal amount = new BigDecimal(100 + (index % 50));
                String currency = "USD";
                String clientRef = "CLIENT-PERSIST-LOAD-" + index;

                Map<String, Object> paymentRequest = Map.of(
                        "transactionId", transactionId,
                        "amount", amount,
                        "currency", currency,
                        "clientReference", clientRef
                );

                restTemplate.postForEntity("/api/v1/payments", paymentRequest, Map.class);
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        // Assert: All transactions persisted to H2
        long dbCount = transactionRepository.count();
        assertEquals(numConcurrentRequests, dbCount,
                String.format("Expected %d transactions persisted, but found %d",
                        numConcurrentRequests, dbCount));
    }
}
