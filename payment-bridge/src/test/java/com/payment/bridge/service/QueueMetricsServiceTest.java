package com.payment.bridge.service;

import com.payment.bridge.repository.DeadLetterQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueMetricsServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private DeadLetterQueueRepository dlqRepository;

    @InjectMocks
    private QueueMetricsService queueMetricsService;

    private Properties paymentQueueProperties;
    private Properties retryQueueProperties;
    private Properties dlqQueueProperties;

    @BeforeEach
    void setUp() {
        paymentQueueProperties = new Properties();
        retryQueueProperties = new Properties();
        dlqQueueProperties = new Properties();
    }

    @Test
    void getQueueMetrics_ReturnsCountsFromAmqpAdmin() {
        paymentQueueProperties.put("QUEUE_MESSAGE_COUNT", 7);
        retryQueueProperties.put("QUEUE_MESSAGE_COUNT", 3);
        dlqQueueProperties.put("QUEUE_MESSAGE_COUNT", 5);

        when(amqpAdmin.getQueueProperties("payment-processing")).thenReturn(paymentQueueProperties);
        when(amqpAdmin.getQueueProperties("payment-retry")).thenReturn(retryQueueProperties);
        when(amqpAdmin.getQueueProperties("dlq-payment-failed")).thenReturn(dlqQueueProperties);
        when(dlqRepository.count()).thenReturn(5L);

        assertThat(queueMetricsService.getPaymentQueueDepth()).isEqualTo(7);
        assertThat(queueMetricsService.getRetryQueueDepth()).isEqualTo(3);
        assertThat(queueMetricsService.getDlqQueueDepth()).isEqualTo(5);
        assertThat(queueMetricsService.getDlqSize()).isEqualTo(5L);
    }
}
