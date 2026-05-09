package com.payment.mock.load;

import com.payment.mock.load.TestHttpConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestHttpConfig.class)
class ConcurrencyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testDataIntegrityUnderConcurrency() throws InterruptedException {
        // Arrange
        int numThreads = 30;
        int requestsPerThread = 15;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act: Send requests and verify data
        for (int t = 0; t < numThreads; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < requestsPerThread; i++) {
                        String transactionId = "INTEGRITY-" + threadNum + "-" + i;
                        BigDecimal expectedAmount = new BigDecimal(100 + i);
                        String currency = "USD";
                        String clientRef = "CLIENT-INT-" + threadNum + "-" + i;

                        Map<String, Object> paymentRequest = Map.of(
                                "transactionId", transactionId,
                                "amount", expectedAmount,
                                "currency", currency,
                                "clientReference", clientRef
                        );

                        ResponseEntity<Map> response = restTemplate.postForEntity(
                                "/api/v1/payments",
                                paymentRequest,
                                Map.class
                        );

                        if (response.getStatusCode().is2xxSuccessful()) {
                            String status = (String) response.getBody().get("status");
                            System.out.println("Status for " + transactionId + ": " + status);
                            if ("COMPLETED".equals(status)) {
                                successCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        latch.await();

        // Assert: Success rate within expected range (90% ± 5%)
        int totalExpected = numThreads * requestsPerThread;
        int expectedSuccess = (int) (totalExpected * 0.9);
        int tolerance = (int) (expectedSuccess * 0.05);
        assertTrue(successCount.get() >= expectedSuccess - tolerance && successCount.get() <= expectedSuccess + tolerance,
                String.format("Success count %d should be around %d ± %d", successCount.get(), expectedSuccess, tolerance));
    }
}
