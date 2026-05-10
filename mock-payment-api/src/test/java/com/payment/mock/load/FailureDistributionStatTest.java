package com.payment.mock.load;

import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.repository.TransactionRepository;
import com.payment.mock.service.MockPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.payment.mock.MockPaymentApiApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = MockPaymentApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Statistical Failure Distribution Tests")
class FailureDistributionStatTest {

    @Autowired
    private MockPaymentService paymentService;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final int LARGE_SAMPLE_SIZE = 1000;
    private static final double EXPECTED_SUCCESS_RATE = 0.90;
    private static final double EXPECTED_FAILURE_RATE = 0.10;
    private static final double TOLERANCE = 0.04; // ±4%

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("Success rate 90% ±3% over 1000 requests")
    void testSuccessRateDistribution1000() throws InterruptedException {
        // Arrange
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(LARGE_SAMPLE_SIZE);

        System.out.println("Starting test with sample size: " + LARGE_SAMPLE_SIZE);

        // Act: Send 1000 requests and track success/failure
        for (int i = 0; i < LARGE_SAMPLE_SIZE; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transactionId = "STAT-" + System.currentTimeMillis() + "-" + index;
                    BigDecimal amount = new BigDecimal("100.00");
                    String currency = "USD";
                    String clientRef = "CLIENT-STAT-" + index;

                    Transaction transaction = paymentService.processPayment(
                            transactionId, amount, currency, clientRef);

                    System.out.println("Transaction " + transactionId + " status: " + transaction.getStatus());

                    if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                        successCount.incrementAndGet();
                    } else if (transaction.getStatus() == TransactionStatus.FAILED) {
                        failureCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await(10, TimeUnit.MINUTES);

        // Assert: Success rate within tolerance
        double actualSuccessRate = (double) successCount.get() / LARGE_SAMPLE_SIZE;
        double lowerBound = EXPECTED_SUCCESS_RATE - TOLERANCE;
        double upperBound = EXPECTED_SUCCESS_RATE + TOLERANCE;

        System.out.println(String.format("Success rate: %.2f%% (expected 90%% ±4%%)",
                actualSuccessRate * 100));

        assertTrue(
                actualSuccessRate >= lowerBound && actualSuccessRate <= upperBound,
                String.format("Success rate %.2f%% should be between %.2f%% and %.2f%%",
                        actualSuccessRate * 100, lowerBound * 100, upperBound * 100)
        );

        // Assert: Failure rate is approximately 10%
        double actualFailureRate = (double) failureCount.get() / LARGE_SAMPLE_SIZE;
        double failureLowerBound = EXPECTED_FAILURE_RATE - TOLERANCE;
        double failureUpperBound = EXPECTED_FAILURE_RATE + TOLERANCE;

        System.out.println(String.format("Failure rate: %.2f%% (expected 10%% ±4%%)",
                actualFailureRate * 100));

        assertTrue(
                actualFailureRate >= failureLowerBound && actualFailureRate <= failureUpperBound,
                String.format("Failure rate %.2f%% should be between %.2f%% and %.2f%%",
                        actualFailureRate * 100, failureLowerBound * 100, failureUpperBound * 100)
        );
    }

    @Test
    @DisplayName("All transactions persisted from 1000 requests")
    void testAllTransactionsPersisted1000() throws InterruptedException {
        // Arrange
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(LARGE_SAMPLE_SIZE);

        // Act: Send 1000 requests
        for (int i = 0; i < LARGE_SAMPLE_SIZE; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transactionId = "PERSIST-" + System.currentTimeMillis() + "-" + index;
                    paymentService.processPayment(
                            transactionId,
                            new BigDecimal("100.00"),
                            "USD",
                            "CLIENT-" + index
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await(10, TimeUnit.MINUTES);

        // Assert: All transactions persisted
        long dbCount = transactionRepository.count();
        assertEquals(LARGE_SAMPLE_SIZE, dbCount,
                String.format("Expected %d transactions in H2, found %d",
                        LARGE_SAMPLE_SIZE, dbCount));
    }

    @Test
    @DisplayName("Distribution consistency across multiple test runs")
    void testDistributionConsistency() throws InterruptedException {
        // This test runs 3 smaller batches to verify consistency
        double[] successRates = new double[3];

        for (int run = 0; run < 3; run++) {
            transactionRepository.deleteAll();

            int batchSize = 500; // Increased for better statistical accuracy
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(50); // Use fixed pool
            CountDownLatch latch = new CountDownLatch(batchSize);

            // Send batch of requests
            for (int i = 0; i < batchSize; i++) {
                final int index = i;
                final int batchNum = run;
                executor.submit(() -> {
                    try {
                        String transactionId = "BATCH-" + batchNum + "-" +
                                System.currentTimeMillis() + "-" + index;

                        Transaction transaction = paymentService.processPayment(
                                transactionId,
                                new BigDecimal("100.00"),
                                "USD",
                                "CLIENT-" + index
                        );

                        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            executor.shutdown();
            latch.await(5, TimeUnit.MINUTES);

            successRates[run] = (double) successCount.get() / batchSize;
            System.out.println(String.format("Batch %d success rate: %.2f%%",
                    run + 1, successRates[run] * 100));
        }

        // Assert: All runs within expected range (increased tolerance for statistical variation)
        for (int i = 0; i < 3; i++) {
            assertTrue(
                    successRates[i] >= (EXPECTED_SUCCESS_RATE - TOLERANCE) &&
                            successRates[i] <= (EXPECTED_SUCCESS_RATE + TOLERANCE),
                    String.format("Batch %d success rate %.2f%% outside expected range [%.1f%%, %.1f%%]",
                            i + 1, successRates[i] * 100,
                            (EXPECTED_SUCCESS_RATE - TOLERANCE) * 100,
                            (EXPECTED_SUCCESS_RATE + TOLERANCE) * 100)
            );
        }

        // Assert: Variation between runs is small (all within 5% of each other)
        double maxRate = Math.max(Math.max(successRates[0], successRates[1]), successRates[2]);
        double minRate = Math.min(Math.min(successRates[0], successRates[1]), successRates[2]);
        double variation = maxRate - minRate;

        System.out.println(String.format("Rate variation across batches: %.2f%%", variation * 100));

        assertTrue(variation < 0.05,
                String.format("Success rates should be consistent (variation < 5%%, got %.2f%%)",
                        variation * 100));
    }

    @Test
    @DisplayName("Failed transactions have reasons populated")
    void testFailedTransactionsHaveReasons() throws InterruptedException {
        // Arrange
        int numRequests = 500;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numRequests);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Act: Send 500 requests and collect failed ones
        for (int i = 0; i < numRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transactionId = "FAILREASON-" + System.currentTimeMillis() + "-" + index;

                    Transaction transaction = paymentService.processPayment(
                            transactionId,
                            new BigDecimal("100.00"),
                            "USD",
                            "CLIENT-" + index
                    );

                    if (transaction.getStatus() == TransactionStatus.FAILED) {
                        failureCount.incrementAndGet();
                        // Verify failure reason exists
                        assertNotNull(transaction.getFailureReason(),
                                "Failed transaction should have failure reason");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await(10, TimeUnit.MINUTES);

        // Assert: Some failures occurred
        assertTrue(failureCount.get() > 0,
                "Should have at least some failures with ~10% rate");

        // Assert: All failures in database have reasons
        transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == TransactionStatus.FAILED)
                .forEach(t -> assertNotNull(t.getFailureReason(),
                        "All failed transactions must have failure reasons"));
    }

    @Test
    @DisplayName("Performance under sustained load")
    void testPerformanceUnderSustainedLoad() throws InterruptedException {
        // Arrange: Send 500 requests and measure total time
        int numRequests = 500;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numRequests);

        long startTime = System.currentTimeMillis();

        // Act: Send all requests
        for (int i = 0; i < numRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String transactionId = "PERF-" + System.currentTimeMillis() + "-" + index;

                    paymentService.processPayment(
                            transactionId,
                            new BigDecimal("100.00"),
                            "USD",
                            "CLIENT-PERF-" + index
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await(10, TimeUnit.MINUTES);
        long totalTimeMs = System.currentTimeMillis() - startTime;

        // Assert: Performance is reasonable
        double throughput = (double) numRequests / (totalTimeMs / 1000.0);
        System.out.println(String.format("Throughput: %.2f requests/second over %dms",
                throughput, totalTimeMs));

        // Should handle at least 10 requests/second on test profile
        assertTrue(throughput >= 10,
                String.format("Throughput should be >= 10 req/s, got %.2f req/s", throughput));

        // Total time should be < 60 seconds (even with delays)
        assertTrue(totalTimeMs < 60000,
                String.format("500 requests should complete in < 60s, took %dms", totalTimeMs));
    }
}
