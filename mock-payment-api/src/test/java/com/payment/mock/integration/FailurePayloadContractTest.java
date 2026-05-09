package com.payment.mock.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "mock.payment.failure-rate=1.0")
@DisplayName("Failure payload contract tests")
class FailurePayloadContractTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void paymentFailureShouldExposeFailureCodeAndReason() {
        Map<String, Object> paymentRequest = Map.of(
                "transactionId", "FAIL-TXN-001",
                "amount", new BigDecimal("10.00"),
                "currency", "USD",
                "clientReference", "CLIENT-FAIL-001"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/payments",
                paymentRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FAILED", response.getBody().get("message"));
        assertEquals("FAILED", response.getBody().get("status"));
        assertNotNull(response.getBody().get("failureCode"));
        assertNotNull(response.getBody().get("failureReason"));
        assertNotNull(response.getBody().get("responseTimeMs"));
        assertEquals("FAIL-TXN-001", response.getBody().get("transactionId"));

        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                "/api/v1/payments/status/FAIL-TXN-001",
                Map.class
        );

        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertNotNull(statusResponse.getBody());
        assertEquals("FAILED", statusResponse.getBody().get("status"));
        assertEquals(response.getBody().get("failureCode"), statusResponse.getBody().get("failureCode"));
        assertEquals(response.getBody().get("failureReason"), statusResponse.getBody().get("failureReason"));
    }
}
