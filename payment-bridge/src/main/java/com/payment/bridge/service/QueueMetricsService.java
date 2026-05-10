package com.payment.bridge.service;

import com.payment.bridge.config.RabbitMQConfig;
import com.payment.bridge.repository.DeadLetterQueueRepository;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;

@Service
public class QueueMetricsService {

    private final AmqpAdmin amqpAdmin;
    private final DeadLetterQueueRepository dlqRepository;

    public QueueMetricsService(AmqpAdmin amqpAdmin, DeadLetterQueueRepository dlqRepository) {
        this.amqpAdmin = amqpAdmin;
        this.dlqRepository = dlqRepository;
    }

    public int getPaymentQueueDepth() {
        return getQueueDepth(RabbitMQConfig.PAYMENT_QUEUE);
    }

    public int getRetryQueueDepth() {
        return getQueueDepth(RabbitMQConfig.RETRY_QUEUE);
    }

    public int getDlqQueueDepth() {
        return getQueueDepth(RabbitMQConfig.DLQ_QUEUE);
    }

    public long getDlqSize() {
        return dlqRepository.count();
    }

    private int getQueueDepth(String queueName) {
        Properties properties = amqpAdmin.getQueueProperties(queueName);
        if (properties == null) {
            return 0;
        }

        Object count = properties.get("QUEUE_MESSAGE_COUNT");
        if (count instanceof Number) {
            return ((Number) count).intValue();
        }
        return 0;
    }
}