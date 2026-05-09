package com.payment.bridge.load;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.payment.bridge.load.LoadTestSupport.executeConcurrentRequestMetrics;
import static com.payment.bridge.load.LoadTestSupport.formatLatencyReport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Payment Bridge Load Tests")
class LatencyLoadTest {

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
    @DisplayName("100 concurrent ingestion requests return 202 accepted under 500ms")
    void testConcurrentIngestionRequestsReturnAcceptedUnderThreshold() throws Exception {
        int requests = 100;
        AtomicInteger acceptedCount = new AtomicInteger();

        LoadTestSupport.LoadTestResult result = executeConcurrentRequestMetrics(requests, 300, index -> {
            Map<String, Object> paymentRequest = Map.of(
                    "amount", new BigDecimal("100.00"),
                    "currency", "USD",
                    "clientReference", "LOAD-CLIENT-" + index
            );

            long elapsed = System.currentTimeMillis();
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "/api/v1/payments",
                    paymentRequest,
                    Map.class
            );
            elapsed = System.currentTimeMillis() - elapsed;

            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                acceptedCount.incrementAndGet();
            }
            return elapsed;
        });

        assertEquals(requests, acceptedCount.get(), "All requests should be accepted");
        assertEquals(requests, paymentRepository.count(), "All payments should be persisted");

        System.out.println(formatLatencyReport(result, "Payment ingestion"));

        assertTrue(result.getP99() <= 500, "99th percentile ingestion latency should be <= 500ms");
        assertTrue(result.getMax() <= 500, "Maximum ingestion latency should be <= 500ms");
    }
}
