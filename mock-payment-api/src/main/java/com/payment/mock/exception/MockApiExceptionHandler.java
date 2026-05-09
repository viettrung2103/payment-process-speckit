package com.payment.mock.exception;

import com.payment.mock.service.TransactionLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class MockApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MockApiExceptionHandler.class);

    @ExceptionHandler(TransactionLookupService.TransactionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionNotFound(
            TransactionLookupService.TransactionNotFoundException ex) {
        log.warn("Transaction not found: {}", ex.getMessage());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "NOT_FOUND");
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "VALIDATION_ERROR");
        response.put("message", "Invalid request parameters");
        response.put("timestamp", LocalDateTime.now());
        response.put("details", ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", "INTERNAL_SERVER_ERROR");
        response.put("message", "An unexpected error occurred");
        response.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
