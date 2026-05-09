package com.payment.bridge.service;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetryHandler exponential backoff calculation and retry logic.
 */
@SpringBootTest
@ActiveProfiles("test")
class RetryHandlerTest {

    @Autowired
    private RetryHandler retryHandler;

    @MockBean
    private PaymentPublisher paymentPublisher;

    @Test
    void testCalculateBackoffDelay_FirstAttempt() {
        long delay = retryHandler.calculateBackoffDelay(1);
        // (1.5^1 - 1) * 1000 = 0.5 * 1000 = 500ms
        assertThat(delay).isEqualTo(500);
    }

    @Test
    void testCalculateBackoffDelay_SecondAttempt() {
        long delay = retryHandler.calculateBackoffDelay(2);
        // (1.5^2 - 1) * 1000 = (2.25 - 1) * 1000 = 1.25 * 1000 = 1250ms
        assertThat(delay).isEqualTo(1250);
    }

    @Test
    void testCalculateBackoffDelay_ThirdAttempt() {
        long delay = retryHandler.calculateBackoffDelay(3);
        // (1.5^3 - 1) * 1000 = (3.375 - 1) * 1000 = 2.375 * 1000 = 2375ms
        assertThat(delay).isEqualTo(2375);
    }

    @Test
    void testCalculateBackoffDelay_FourthAttempt() {
        long delay = retryHandler.calculateBackoffDelay(4);
        // (1.5^4 - 1) * 1000 = (5.0625 - 1) * 1000 = 4.0625 * 1000 = 4062ms
        assertThat(delay).isEqualTo(4062);
    }

    @Test
    void testCalculateBackoffDelay_FifthAttempt() {
        long delay = retryHandler.calculateBackoffDelay(5);
        // (1.5^5 - 1) * 1000 = (7.59375 - 1) * 1000 = 6.59375 * 1000 = 6593ms
        assertThat(delay).isEqualTo(6593);
    }

    @Test
    void testCalculateBackoffDelay_ZeroAttempt() {
        long delay = retryHandler.calculateBackoffDelay(0);
        assertThat(delay).isEqualTo(0);
    }

    @Test
    void testCalculateBackoffDelay_NegativeAttempt() {
        long delay = retryHandler.calculateBackoffDelay(-1);
        assertThat(delay).isEqualTo(0);
    }

    @Test
    void testCalculateBackoffDelay_CappedAt5Minutes() {
        // Test with a high attempt number that would exceed 5 minutes
        long delay = retryHandler.calculateBackoffDelay(20);
        assertThat(delay).isEqualTo(5 * 60 * 1000); // 5 minutes in milliseconds
    }

    @Test
    void testScheduleRetry_FirstRetry() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());
        task.setRetryAttempt(0);

        boolean result = retryHandler.scheduleRetry(task);

        assertThat(result).isTrue();
        assertThat(task.getRetryAttempt()).isEqualTo(1);
        verify(paymentPublisher).publishPaymentTaskWithDelay(task, 500L);
    }

    @Test
    void testScheduleRetry_SecondRetry() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());
        task.setRetryAttempt(1);

        boolean result = retryHandler.scheduleRetry(task);

        assertThat(result).isTrue();
        assertThat(task.getRetryAttempt()).isEqualTo(2);
        verify(paymentPublisher).publishPaymentTaskWithDelay(task, 1250L);
    }

    @Test
    void testScheduleRetry_MaxRetriesExceeded() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());
        task.setRetryAttempt(5);

        boolean result = retryHandler.scheduleRetry(task);

        assertThat(result).isFalse();
        assertThat(task.getRetryAttempt()).isEqualTo(5); // Should not increment
        verify(paymentPublisher, never()).publishPaymentTaskWithDelay(any(), anyLong());
    }

    @Test
    void testHasExceededMaxRetries_BelowLimit() {
        MessageQueueTask task = new MessageQueueTask();
        task.setRetryAttempt(3);

        boolean exceeded = retryHandler.hasExceededMaxRetries(task);

        assertThat(exceeded).isFalse();
    }

    @Test
    void testHasExceededMaxRetries_AtLimit() {
        MessageQueueTask task = new MessageQueueTask();
        task.setRetryAttempt(5);

        boolean exceeded = retryHandler.hasExceededMaxRetries(task);

        assertThat(exceeded).isTrue();
    }

    @Test
    void testHasExceededMaxRetries_AboveLimit() {
        MessageQueueTask task = new MessageQueueTask();
        task.setRetryAttempt(6);

        boolean exceeded = retryHandler.hasExceededMaxRetries(task);

        assertThat(exceeded).isTrue();
    }
}