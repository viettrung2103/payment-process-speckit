package com.payment.mock.exception;

import com.payment.mock.service.TransactionLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MockApiExceptionHandler Tests")
class MockApiExceptionHandlerTest {

    private final MockApiExceptionHandler exceptionHandler = new MockApiExceptionHandler();

    @Test
    @DisplayName("TransactionNotFoundException should return 404 with error details")
    void transactionNotFoundExceptionShouldReturn404() {
        TransactionLookupService.TransactionNotFoundException ex =
            new TransactionLookupService.TransactionNotFoundException("Transaction TXN-123 not found");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleTransactionNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertEquals("NOT_FOUND", body.get("error"));
        assertEquals("Transaction TXN-123 not found", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("Generic Exception should return 500 with error details")
    void genericExceptionShouldReturn500() {
        RuntimeException ex = new RuntimeException("Unexpected error occurred");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertEquals("INTERNAL_SERVER_ERROR", body.get("error"));
        assertEquals("An unexpected error occurred", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException should return 400 with validation details")
    void methodArgumentNotValidExceptionShouldReturn400() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("paymentRequest", "amount", "Amount must be positive");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertEquals("VALIDATION_ERROR", body.get("error"));
        assertEquals("Invalid request parameters", body.get("message"));
        assertNotNull(body.get("timestamp"));
        assertNotNull(body.get("details"));
        assertTrue(body.get("details") instanceof List);
    }
}