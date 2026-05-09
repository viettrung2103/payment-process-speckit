package com.payment.mock.controller;

import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.service.TransactionLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController Unit Tests")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionLookupService transactionLookupService;

    @Test
    @DisplayName("GET transaction by id returns 200 and details")
    void shouldReturnTransactionById() throws Exception {
        Transaction transaction = new Transaction("TXN-789", new BigDecimal("80.00"), "GBP", "CLIENT-003");
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setResponseTimeMs(123L);

        when(transactionLookupService.findByTransactionIdOrThrow("TXN-789"))
                .thenReturn(transaction);

        mockMvc.perform(get("/api/v1/transactions/TXN-789")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-789"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.responseTimeMs").isNumber());
    }

    @Test
    @DisplayName("GET transaction history returns paged results")
    void shouldReturnPagedTransactionHistory() throws Exception {
        Transaction txn1 = new Transaction("TXN-101", new BigDecimal("20.00"), "USD", "CLIENT-A");
        Transaction txn2 = new Transaction("TXN-102", new BigDecimal("30.00"), "EUR", "CLIENT-B");
        txn1.setStatus(TransactionStatus.COMPLETED);
        txn2.setStatus(TransactionStatus.COMPLETED);

        Page<Transaction> page = new PageImpl<>(List.of(txn1, txn2), PageRequest.of(0, 2), 2);
        when(transactionLookupService.findAll(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions?limit=2&offset=0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.totalCount").value(2));
    }
}
