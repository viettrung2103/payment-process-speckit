package com.payment.bridge.worker;

import com.payment.bridge.config.RabbitMQConfig;
import com.payment.bridge.exception.PaymentProcessingException;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.service.DLQHandler;
import com.payment.bridge.service.ErrorClassifier;
import com.payment.bridge.service.PaymentAuditService;
import com.payment.bridge.service.PaymentService;
import com.payment.bridge.service.RetryHandler;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@Profile("!integration")
public class PaymentWorker {

    public enum ProcessingResult {
        SUCCESS, RETRY_SCHEDULED, FAILED
    }

    private static final Logger logger = LoggerFactory.getLogger(PaymentWorker.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ErrorClassifier errorClassifier;
    private final RetryHandler retryHandler;
    private final DLQHandler dlqHandler;
    private final PaymentAuditService paymentAuditService;

    public PaymentWorker(PaymentRepository paymentRepository,
                        PaymentService paymentService,
                        ErrorClassifier errorClassifier,
                        RetryHandler retryHandler,
                        DLQHandler dlqHandler,
                        PaymentAuditService paymentAuditService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.errorClassifier = errorClassifier;
        this.retryHandler = retryHandler;
        this.dlqHandler = dlqHandler;
        this.paymentAuditService = paymentAuditService;
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void receive(MessageQueueTask task, Message message, Channel channel) {
        try {
            ProcessingResult result = processPaymentTask(task);
            try {
                switch (result) {
                    case SUCCESS:
                        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                        logger.info("Acknowledged payment task: {}", task.getPaymentId());
                        break;
                    case RETRY_SCHEDULED:
                        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                        logger.info("NACKed payment task for retry: {}", task.getPaymentId());
                        break;
                    case FAILED:
                        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                        logger.error("NACKed failed payment task: {}", task.getPaymentId());
                        break;
                }
            } catch (IOException ioException) {
                logger.error("Failed to ACK/NACK message for payment {}", task.getPaymentId(), ioException);
            }
        } catch (Exception e) {
            try {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
                logger.error("NACKed payment task due to processing exception: {}", task.getPaymentId(), e);
            } catch (IOException ioException) {
                logger.error("Failed to NACK message for payment {} after processing exception", task.getPaymentId(), ioException);
            }
        }
    }

    public ProcessingResult processPaymentTask(MessageQueueTask task) {
        logger.info("Processing payment task: {} retryAttempt={} ", task.getPaymentId(), task.getRetryAttempt());

        Optional<Payment> paymentOpt = paymentRepository.findById(task.getPaymentId());
        if (paymentOpt.isEmpty()) {
            logger.warn("Payment not found for task: {}", task.getPaymentId());
            throw new PaymentProcessingException("Payment not found for task: " + task.getPaymentId());
        }

        Payment payment = paymentOpt.get();

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            logger.info("Skipping already completed payment task: {}", task.getPaymentId());
            return ProcessingResult.SUCCESS;
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            logger.info("Skipping already failed payment task: {}", task.getPaymentId());
            return ProcessingResult.FAILED;
        }

        try {
            // Only update status to IN_PROGRESS if this is the first attempt and we are still in RECEIVED state
            if (task.getRetryAttempt() == 0 && payment.getStatus() == PaymentStatus.RECEIVED) {
                payment.setStatus(PaymentStatus.IN_PROGRESS);
                paymentRepository.save(payment);
            }

            paymentService.processPaymentWithExternalAPI(task.getPaymentId());

            logger.info("Successfully processed payment {} on attempt {}", task.getPaymentId(), task.getRetryAttempt() + 1);
            return ProcessingResult.SUCCESS;

        } catch (Exception e) {
            logger.error("Payment {} failed on attempt {}: {}", task.getPaymentId(), task.getRetryAttempt() + 1, e.getMessage());

            // Determine if this is a retryable error
            ErrorClassifier.ErrorAction action = errorClassifier.classify(e);

            if (action == ErrorClassifier.ErrorAction.RETRY && !retryHandler.hasExceededMaxRetries(task)) {
                // Schedule retry
                boolean retryScheduled = retryHandler.scheduleRetry(task);
                if (retryScheduled) {
                    logger.info("Scheduled retry for payment {} (attempt {})", task.getPaymentId(), task.getRetryAttempt() + 1);
                    return ProcessingResult.RETRY_SCHEDULED;
                }
            }

            // Either non-retryable error or max retries exceeded - send to DLQ
            logger.error("Sending payment {} to DLQ after {} attempts", task.getPaymentId(), task.getRetryAttempt() + 1);

            // Update payment status to FAILED
            PaymentStatus oldStatus = payment.getStatus();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorReason(e.getMessage());
            paymentRepository.save(payment);
            paymentAuditService.recordTransition(payment, oldStatus, PaymentStatus.FAILED,
                    "Transitioned to failed after retry exhaustion or non-retryable error", "worker");

            // Create DLQ entry
            dlqHandler.createAPIFailureDLQEntry(payment, task, null, e);

            return ProcessingResult.FAILED;
        }
    }
}
