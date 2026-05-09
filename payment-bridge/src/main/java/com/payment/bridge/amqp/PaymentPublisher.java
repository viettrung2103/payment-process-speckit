package com.payment.bridge.amqp;

import com.payment.bridge.config.RabbitMQConfig;
import com.payment.bridge.model.MessageQueueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!integration")
public class PaymentPublisher implements PaymentTaskPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentTask(MessageQueueTask task) {
        try {
            logger.info("Publishing payment task: {}", task.getPaymentId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EXCHANGE, RabbitMQConfig.PAYMENT_ROUTING_KEY, task);
            logger.debug("Successfully published payment task: {}", task.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish payment task: {}", task.getPaymentId(), e);
            throw new RuntimeException("Failed to publish payment task", e);
        }
    }

    public void publishRetryTask(MessageQueueTask task) {
        try {
            logger.info("Publishing retry task: {}", task.getPaymentId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EXCHANGE, RabbitMQConfig.RETRY_ROUTING_KEY, task);
            logger.debug("Successfully published retry task: {}", task.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish retry task: {}", task.getPaymentId(), e);
            throw new RuntimeException("Failed to publish retry task", e);
        }
    }

    public void publishPaymentTaskWithDelay(MessageQueueTask task, long delayMillis) {
        try {
            logger.info("Publishing payment task with delay {}ms: {}", delayMillis, task.getPaymentId());
            rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EXCHANGE, RabbitMQConfig.RETRY_ROUTING_KEY, task,
                (MessagePostProcessor) message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                    return message;
                });
            logger.debug("Successfully published delayed payment task: {}", task.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish delayed payment task: {}", task.getPaymentId(), e);
            throw new RuntimeException("Failed to publish delayed payment task", e);
        }
    }
}