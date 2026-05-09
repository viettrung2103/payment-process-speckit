package com.payment.bridge.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("integration")
public class IntegrationPaymentPublisher implements PaymentTaskPublisher {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationPaymentPublisher.class);

    private final PaymentRepository paymentRepository;
    private final ExternalApiClient externalApiClient;
    private final ObjectMapper objectMapper;

    public IntegrationPaymentPublisher(PaymentRepository paymentRepository,
                                       ExternalApiClient externalApiClient,
                                       ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.externalApiClient = externalApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishPaymentTask(MessageQueueTask task) {
        logger.info("Integration profile: processing payment task immediately: {}", task.getPaymentId());
        processPaymentTask(task);
    }

    @Override
    public void publishRetryTask(MessageQueueTask task) {
        logger.info("Integration profile: processing retry payment task immediately: {}", task.getPaymentId());
        processPaymentTask(task);
    }

    @Override
    public void publishPaymentTaskWithDelay(MessageQueueTask task, long delayMillis) {
        logger.info("Integration profile: processing delayed payment task immediately (delay ignored): {}", task.getPaymentId());
        processPaymentTask(task);
    }

    private void processPaymentTask(MessageQueueTask task) {
        Payment payment = paymentRepository.findById(task.getPaymentId())
                .orElseThrow(() -> new PaymentProcessingException("Payment not found: " + task.getPaymentId()));

        payment.setStatus(PaymentStatus.IN_PROGRESS);
        paymentRepository.save(payment);

        try {
            var apiResponse = externalApiClient.processPayment(payment);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setExternalTransactionId(apiResponse.getTransactionId());
            payment.setApiStatusCode(apiResponse.getStatusCode());
            try {
                payment.setApiResponse(objectMapper.writeValueAsString(apiResponse));
            } catch (JsonProcessingException e) {
                logger.warn("Failed to serialize API response for payment {}, storing plain string", payment.getPaymentId(), e);
                payment.setApiResponse(apiResponse.toString());
            }
            paymentRepository.save(payment);
        } catch (Exception e) {
            logger.error("Integration profile payment {} failed during external API processing", task.getPaymentId(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorReason(e.getMessage());
            paymentRepository.save(payment);
            throw e instanceof RuntimeException ? (RuntimeException) e : new PaymentProcessingException("Payment processing failed", e);
        }
    }
}
