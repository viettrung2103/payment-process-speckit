package com.payment.mock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.mock.dto.PaymentRequest;
import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.service.MockPaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Unit Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MockPaymentService paymentService;

    @Test
    @DisplayName("Valid payment request should return 200 and transaction details")
    void shouldReturn200ForValidRequest() throws Exception {
        Transaction transaction = new Transaction("TXN-123", new BigDecimal("150.00"), "USD", "CLIENT-001");
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(java.time.LocalDateTime.now());

        when(paymentService.processPayment(eq("TXN-123"), any(), eq("USD"), eq("CLIENT-001")))
                .thenReturn(transaction);

        PaymentRequest request = new PaymentRequest("TXN-123", new BigDecimal("150.00"), "USD", "CLIENT-001");

        mockMvc.perform(post("/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    @DisplayName("Invalid payment request should return 400")
    void shouldReturn400ForInvalidRequest() throws Exception {
        PaymentRequest request = new PaymentRequest("", new BigDecimal("0.00"), "US", "");

        mockMvc.perform(post("/api/v1/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET payment status should return transaction details")
    void shouldReturnPaymentStatus() throws Exception {
        Transaction transaction = new Transaction("TXN-456", new BigDecimal("200.00"), "EUR", "CLIENT-002");
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setProcessedAt(java.time.LocalDateTime.now());

        when(paymentService.getTransaction("TXN-456")).thenReturn(transaction);

        mockMvc.perform(get("/api/v1/payments/status/TXN-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-456"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
