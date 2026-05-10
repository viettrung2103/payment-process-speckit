package com.payment.bridge.controller;

import com.payment.bridge.model.PaymentRequest;
import com.payment.bridge.model.PaymentResponse;
import com.payment.bridge.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private Executor taskExecutor;
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        taskExecutor = Executors.newSingleThreadExecutor();
        paymentController = new PaymentController(paymentService, taskExecutor);
    }

    @Test
    void createPayment_shouldReturnAcceptedResponseAsync() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(100));
        request.setCurrency("USD");
        request.setClientReference("test-ref");

        PaymentResponse expectedResponse = new PaymentResponse();
        expectedResponse.setPaymentId(UUID.randomUUID());
        expectedResponse.setStatus("RECEIVED");
        expectedResponse.setMessage("Payment request accepted for processing");

        when(paymentService.createPayment(eq(request), eq("idempotency-key"))).thenReturn(expectedResponse);

        CompletableFuture<org.springframework.http.ResponseEntity<PaymentResponse>> future =
            paymentController.createPayment("idempotency-key", null, request);

        org.springframework.http.ResponseEntity<PaymentResponse> responseEntity = future.join();

        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(202);
        assertThat(responseEntity.getBody()).isEqualTo(expectedResponse);
        assertThat(responseEntity.getHeaders().getFirst("X-Payment-ID")).isEqualTo(expectedResponse.getPaymentId().toString());
        assertThat(responseEntity.getBody().getLinks()).containsKeys("self", "status");

        verify(paymentService).createPayment(eq(request), eq("idempotency-key"));
    }

    @Test
    void createPayment_shouldExecuteOnProvidedAsyncExecutor() {
        PaymentRequest request = new PaymentRequest();
        request.setAmount(BigDecimal.valueOf(20));
        request.setCurrency("USD");
        request.setClientReference("async-check");

        PaymentResponse expectedResponse = new PaymentResponse();
        expectedResponse.setPaymentId(UUID.randomUUID());
        expectedResponse.setStatus("RECEIVED");
        expectedResponse.setMessage("Payment request accepted for processing");

        when(paymentService.createPayment(eq(request), eq(null))).thenReturn(expectedResponse);

        AtomicReference<String> executionThread = new AtomicReference<>();
        Executor recordingExecutor = command -> new Thread(() -> {
            executionThread.set(Thread.currentThread().getName());
            command.run();
        }, "payment-controller-async-thread").start();

        paymentController = new PaymentController(paymentService, recordingExecutor);

        CompletableFuture<org.springframework.http.ResponseEntity<PaymentResponse>> future =
            paymentController.createPayment(null, null, request);

        org.springframework.http.ResponseEntity<PaymentResponse> responseEntity = future.join();

        assertThat(responseEntity.getStatusCodeValue()).isEqualTo(202);
        assertThat(responseEntity.getBody()).isEqualTo(expectedResponse);
        assertThat(executionThread.get()).isEqualTo("payment-controller-async-thread");
    }
}
