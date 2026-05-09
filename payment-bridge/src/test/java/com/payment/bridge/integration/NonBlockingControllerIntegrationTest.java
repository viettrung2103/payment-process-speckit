package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.amqp.PaymentTaskPublisher;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Non-blocking Controller Integration Tests")
class NonBlockingControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentTaskPublisher paymentTaskPublisher;

    @MockBean
    private PaymentPublisher paymentPublisher;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Controller returns quickly while background publishing is slow")
    void testControllerRemainsResponsiveDuringSlowBackgroundPublishing() throws Exception {
        doAnswer(invocation -> {
            Thread.sleep(2000);
            return null;
        }).when(paymentTaskPublisher).publishPaymentTask(any());

        int requests = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Long>> timings = new ArrayList<>(requests);
        AtomicInteger acceptedCount = new AtomicInteger();

        for (int i = 0; i < requests; i++) {
            final int index = i;
            Future<Long> future = executor.submit(() -> {
                Map<String, Object> paymentRequest = Map.of(
                        "amount", new BigDecimal("55.00"),
                        "currency", "USD",
                        "clientReference", "NB-CLIENT-" + index
                );

                Instant start = Instant.now();
                ResponseEntity<Map> response = restTemplate.postForEntity(
                        "/api/v1/payments",
                        paymentRequest,
                        Map.class
                );
                long elapsed = Duration.between(start, Instant.now()).toMillis();

                if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                    acceptedCount.incrementAndGet();
                }
                return elapsed;
            });
            timings.add(future);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.MINUTES), "Requests should complete within 3 minutes");

        List<Long> durations = new ArrayList<>(requests);
        for (Future<Long> future : timings) {
            durations.add(future.get(10, TimeUnit.SECONDS));
        }

        assertEquals(requests, acceptedCount.get(), "All ingestion requests should be accepted");
        assertEquals(requests, paymentRepository.count(), "All payments should be persisted before returning");

        durations.sort(Long::compareTo);
        long max = durations.get(durations.size() - 1);

        System.out.println("Non-blocking controller max ingestion latency = " + max + "ms");

        assertTrue(max <= 500, "Controller should return within 500ms even when publishing is slow");
    }
}
