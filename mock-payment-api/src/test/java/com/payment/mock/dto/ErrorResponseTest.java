package com.payment.mock.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorResponse DTO Tests")
class ErrorResponseTest {

    @Test
    @DisplayName("Default constructor should create empty object")
    void defaultConstructorShouldCreateEmptyObject() {
        ErrorResponse response = new ErrorResponse();

        assertNull(response.getCode());
        assertNull(response.getMessage());
        assertNull(response.getErrorCode());
        assertNull(response.getTransactionId());
        assertNull(response.getRetryAfter());
    }

    @Test
    @DisplayName("Parameterized constructor should set all fields")
    void parameterizedConstructorShouldSetAllFields() {
        ErrorResponse response = new ErrorResponse(400, "Bad Request", "VALIDATION_ERROR", "TXN-123", 30);

        assertEquals(400, response.getCode());
        assertEquals("Bad Request", response.getMessage());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertEquals("TXN-123", response.getTransactionId());
        assertEquals(30, response.getRetryAfter());
    }

    @Test
    @DisplayName("Setters and getters should work correctly")
    void settersAndGettersShouldWorkCorrectly() {
        ErrorResponse response = new ErrorResponse();

        response.setCode(500);
        response.setMessage("Internal Server Error");
        response.setErrorCode("INTERNAL_ERROR");
        response.setTransactionId("TXN-456");
        response.setRetryAfter(60);

        assertEquals(500, response.getCode());
        assertEquals("Internal Server Error", response.getMessage());
        assertEquals("INTERNAL_ERROR", response.getErrorCode());
        assertEquals("TXN-456", response.getTransactionId());
        assertEquals(60, response.getRetryAfter());
    }

    @Test
    @DisplayName("Partial field setting should work")
    void partialFieldSettingShouldWork() {
        ErrorResponse response = new ErrorResponse();
        response.setCode(404);
        response.setMessage("Not Found");

        assertEquals(404, response.getCode());
        assertEquals("Not Found", response.getMessage());
        assertNull(response.getErrorCode());
        assertNull(response.getTransactionId());
        assertNull(response.getRetryAfter());
    }
}