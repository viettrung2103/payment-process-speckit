package com.payment.bridge.amqp;

import com.payment.bridge.model.MessageQueueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PaymentPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PaymentPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentTask(MessageQueueTask task) {
        try {
            logger.info("Publishing payment task: {}", task.getPaymentId());
            rabbitTemplate.convertAndSend("payment.exchange", "payment.process", task);
            logger.debug("Successfully published payment task: {}", task.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish payment task: {}", task.getPaymentId(), e);
            throw new RuntimeException("Failed to publish payment task", e);
        }
    }

    public void publishRetryTask(MessageQueueTask task) {
        try {
            logger.info("Publishing retry task: {}", task.getPaymentId());
            rabbitTemplate.convertAndSend("payment.exchange", "payment.retry", task);
            logger.debug("Successfully published retry task: {}", task.getPaymentId());
        } catch (Exception e) {
            logger.error("Failed to publish retry task: {}", task.getPaymentId(), e);
            throw new RuntimeException("Failed to publish retry task", e);
        }
    }
}