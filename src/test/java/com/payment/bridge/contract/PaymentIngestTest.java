package com.payment.bridge.contract;

import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentIngestTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }

    @Test
    void createPayment_shouldReturn202WithValidRequest() {
        // Given
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(99.99));
        request.setCurrency("USD");
        request.setClientReference("test-order-123");

        String idempotencyKey = UUID.randomUUID().toString();

        // When & Then
        PaymentResponse response = given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", idempotencyKey)
            .header("X-API-Key", "test-key")
            .body(request)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(202)
            .header("Location", notNullValue())
            .header("X-Payment-ID", notNullValue())
            .body("paymentId", notNullValue())
            .body("status", equalTo("RECEIVED"))
            .body("message", equalTo("Payment request accepted for processing"))
            .body("estimatedProcessingTime", equalTo("PT30S"))
            .body("_links.self.href", notNullValue())
            .extract()
            .as(PaymentResponse.class);

        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void createPayment_shouldReturn400ForInvalidAmount() {
        // Given
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setCurrency("USD");

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .body(request)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(400)
            .body("status", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_shouldReturn400ForInvalidCurrency() {
        // Given
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("INVALID");

        // When & Then
        given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", UUID.randomUUID().toString())
            .body(request)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(400)
            .body("status", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void createPayment_shouldReturn409ForDuplicateIdempotencyKey() {
        // Given
        PaymentRequest request1 = new PaymentRequest();
        request1.setAmount(BigDecimal.valueOf(100.00));
        request1.setCurrency("USD");
        request1.setClientReference("order-123");

        PaymentRequest request2 = new PaymentRequest();
        request2.setAmount(BigDecimal.valueOf(200.00)); // Different amount
        request2.setCurrency("USD");
        request2.setClientReference("order-123");

        String idempotencyKey = UUID.randomUUID().toString();

        // First request - should succeed
        given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", idempotencyKey)
            .body(request1)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(202);

        // Second request with same key but different content - should fail
        given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", idempotencyKey)
            .body(request2)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(409)
            .body("status", equalTo("DUPLICATE_REQUEST"));
    }

    @Test
    void createPayment_shouldHandleIdempotentRequests() {
        // Given
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setCurrency("EUR");
        request.setClientReference("order-456");

        String idempotencyKey = UUID.randomUUID().toString();

        // First request
        PaymentResponse response1 = given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", idempotencyKey)
            .body(request)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(202)
            .extract()
            .as(PaymentResponse.class);

        // Second identical request - should return same payment
        PaymentResponse response2 = given()
            .contentType(ContentType.JSON)
            .header("X-Idempotency-Key", idempotencyKey)
            .body(request)
        .when()
            .post("/api/v1/payments")
        .then()
            .statusCode(202)
            .extract()
            .as(PaymentResponse.class);

        // Should return the same payment ID
        assertThat(response2.getPaymentId()).isEqualTo(response1.getPaymentId());
    }
}