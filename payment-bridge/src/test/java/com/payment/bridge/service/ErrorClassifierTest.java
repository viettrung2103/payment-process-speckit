package com.payment.bridge.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorClassifier error classification logic.
 */
@SpringBootTest
@ActiveProfiles("test")
class ErrorClassifierTest {

    @Autowired
    private ErrorClassifier errorClassifier;

    @Test
    void testClassify_4xxStatusCodes_ReturnDLQ() {
        // 4xx client errors should go to DLQ immediately
        assertThat(errorClassifier.classify(400)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(401)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(403)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(404)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(422)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(429)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
    }

    @Test
    void testClassify_5xxStatusCodes_ReturnRetry() {
        // Most 5xx server errors should be retried
        assertThat(errorClassifier.classify(500)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(502)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(503)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(504)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(505)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
    }

    @Test
    void testClassify_501StatusCode_ReturnDLQ() {
        // 501 Not Implemented should go to DLQ
        assertThat(errorClassifier.classify(501)).isEqualTo(ErrorClassifier.ErrorAction.DLQ);
    }

    @Test
    void testClassify_NetworkExceptions_ReturnRetry() {
        // Network-level exceptions should be retried
        assertThat(errorClassifier.classify(new java.net.SocketTimeoutException()))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(new java.net.ConnectException()))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(new java.io.IOException()))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(new javax.net.ssl.SSLException("SSL error")))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
    }

    @Test
    void testClassify_OptimisticLockingExceptions_ReturnRetry() {
        assertThat(errorClassifier.classify(new org.springframework.dao.OptimisticLockingFailureException("Optimistic lock")))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(new jakarta.persistence.OptimisticLockException("Optimistic lock")))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
    }

    @Test
    void testClassify_ObjectOptimisticLockingFailureException_ReturnRetry() {
        assertThat(errorClassifier.classify(new org.springframework.orm.ObjectOptimisticLockingFailureException("Payment", "Optimistic lock")))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(new RuntimeException(new org.springframework.orm.ObjectOptimisticLockingFailureException("Payment", "Optimistic lock"))))
            .isEqualTo(ErrorClassifier.ErrorAction.RETRY);
    }

    @Test
    void testClassify_OtherExceptions_ReturnDLQ() {
        // Other exceptions (like runtime exceptions) should go to DLQ
        assertThat(errorClassifier.classify(new RuntimeException()))
            .isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(new IllegalArgumentException()))
            .isEqualTo(ErrorClassifier.ErrorAction.DLQ);
        assertThat(errorClassifier.classify(new NullPointerException()))
            .isEqualTo(ErrorClassifier.ErrorAction.DLQ);
    }

    @Test
    void testClassify_UnexpectedStatusCodes_ReturnRetry() {
        // Unexpected status codes should be retried (defensive programming)
        assertThat(errorClassifier.classify(200)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(300)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(600)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
        assertThat(errorClassifier.classify(999)).isEqualTo(ErrorClassifier.ErrorAction.RETRY);
    }
}