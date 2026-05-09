package com.payment.mock.service;

import com.payment.mock.entity.Transaction;
import com.payment.mock.entity.TransactionErrorCode;
import com.payment.mock.entity.TransactionStatus;
import com.payment.mock.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MockPaymentService Tests")
class MockPaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FailureSimulator failureSimulator;

    @Mock
    private DelaySimulator delaySimulator;

    private MockPaymentService mockPaymentService;

    @BeforeEach
    void setUp() {
        mockPaymentService = new MockPaymentService(transactionRepository, failureSimulator, delaySimulator);
    }

    @Test
    @DisplayName("processPayment returns transaction with COMPLETED status on success")
    void testProcessPaymentSuccess() {
        // Arrange
        String transactionId = "TXN-123";
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";
        String clientRef = "CLIENT-001";

        when(transactionRepository.existsByTransactionId(transactionId)).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(failureSimulator.shouldFail()).thenReturn(false);

        // Act
        Transaction result = mockPaymentService.processPayment(transactionId, amount, currency, clientRef);

        // Assert
        assertNotNull(result);
        assertEquals(transactionId, result.getTransactionId());
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        assertEquals(amount, result.getAmount());
        assertEquals(currency, result.getCurrency());
    }

    @Test
    @DisplayName("processPayment returns transaction with FAILED status on failure")
    void testProcessPaymentFailure() {
        // Arrange
        String transactionId = "TXN-456";
        BigDecimal amount = new BigDecimal("50.00");
        String currency = "EUR";
        String clientRef = "CLIENT-002";

        when(transactionRepository.existsByTransactionId(transactionId)).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(failureSimulator.shouldFail()).thenReturn(true);
        when(failureSimulator.generateFailureScenario()).thenReturn(TransactionErrorCode.SERVICE_UNAVAILABLE);

        // Act
        Transaction result = mockPaymentService.processPayment(transactionId, amount, currency, clientRef);

        // Assert
        assertNotNull(result);
        assertEquals(transactionId, result.getTransactionId());
        assertEquals(TransactionStatus.FAILED, result.getStatus());
        assertNotNull(result.getFailureReason());
    }

    @Test
    @DisplayName("processPayment returns existing transaction if already exists")
    void testProcessPaymentDuplicate() {
        // Arrange
        String transactionId = "TXN-DUP";
        BigDecimal amount = new BigDecimal("75.00");
        String currency = "GBP";
        String clientRef = "CLIENT-003";

        Transaction existingTxn = new Transaction(transactionId, amount, currency, clientRef);
        existingTxn.setStatus(TransactionStatus.COMPLETED);

        when(transactionRepository.existsByTransactionId(transactionId)).thenReturn(true);
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(existingTxn));

        // Act
        Transaction result = mockPaymentService.processPayment(transactionId, amount, currency, clientRef);

        // Assert
        assertNotNull(result);
        assertEquals(transactionId, result.getTransactionId());
        assertEquals(TransactionStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("getTransaction throws RuntimeException if not found")
    void testGetTransactionNotFound() {
        // Arrange
        String transactionId = "TXN-NOT-FOUND";
        when(transactionRepository.findByTransactionId(transactionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> mockPaymentService.getTransaction(transactionId));
    }
}
