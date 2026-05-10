package com.payment.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("MockPaymentApiApplication Tests")
class MockPaymentApiApplicationTest {

    @Test
    @DisplayName("Application context should load successfully")
    void applicationContextShouldLoadSuccessfully() {
        // This test verifies that the Spring Boot application context loads without errors
        // The @SpringBootTest annotation ensures the full application context is loaded
        // If the main method or application configuration has issues, this test will fail
    }
}