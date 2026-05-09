package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.client.ExternalApiClient;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import com.payment.bridge.service.PaymentService;
import com.payment.bridge.worker.PaymentWorker;
import com.rabbitmq.client.Channel;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for worker crash recovery scenarios.
 * Verifies at-least-once message processing semantics with RabbitMQ redelivery.
 */
@SpringBootTest
@ActiveProfiles("test")
class WorkerRecoveryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ExternalApiClient externalApiClient;

    @Autowired
    private PaymentWorker paymentWorker;

    @Test
    @Transactional
    void testWorkerCrashRecoveryAtLeastOnceSemantics() throws IOException {
        // Given: A payment in RECEIVED state ready for processing
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment savedPayment = paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Create message and channel without Mockito to avoid inline mock limitations
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(1L);
        Message message = new Message(new byte[0], messageProperties);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean nacked = new AtomicBoolean(false);
        Channel channel = createTestChannel(acked, nacked);

        // Simulate external API failure (worker crash scenario)
        ((WorkerRecoveryTest.StubExternalApiClient) externalApiClient)
                .setBehavior(paymentArg -> { throw new RuntimeException("Worker crash during API call"); });

        // When: Worker processes the task but crashes (external API fails)
        paymentWorker.receive(task, message, channel);

        // Then: Message should be NACKed (not acknowledged) for redelivery
        assertFalse(acked.get(), "Channel should not ack the failed task");
        assertTrue(nacked.get(), "Channel should nack the failed task");

        // And: Payment status should be FAILED (updated by processPaymentWithExternalAPI on failure)
        Optional<Payment> recoveredPayment = paymentRepository.findById(paymentId);
        assertThat(recoveredPayment).isPresent();
        assertThat(recoveredPayment.get().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @Transactional
    void testWorkerSuccessfulProcessingWithAck() throws IOException {
        // Given: A payment in RECEIVED state ready for processing
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);

        Payment savedPayment = paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setRetryAttempt(0);

        // Mock successful external API call via the stub client
        ((WorkerRecoveryTest.StubExternalApiClient) externalApiClient)
                .setBehavior(paymentArg -> {
                    ExternalApiClient.ApiResponse apiResponse = new ExternalApiClient.ApiResponse();
                    apiResponse.setTransactionId(paymentId.toString());
                    apiResponse.setStatus("SUCCESS");
                    apiResponse.setStatusCode(200);
                    return apiResponse;
                });

        // Create message and channel without Mockito
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(1L);
        Message message = new Message(new byte[0], messageProperties);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean nacked = new AtomicBoolean(false);
        Channel channel = createTestChannel(acked, nacked);

        // When: Worker successfully processes the task
        paymentWorker.receive(task, message, channel);

        // Then: Message should be ACKed
        assertTrue(acked.get(), "Channel should ack the successfully processed task");
        assertFalse(nacked.get(), "Channel should not nack the successfully processed task");

        // And: Payment status should be updated to COMPLETED
        Optional<Payment> processedPayment = paymentRepository.findById(paymentId);
        assertThat(processedPayment).isPresent();
        assertThat(processedPayment.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @Transactional
    void testWorkerHandlesMissingPaymentGracefully() throws IOException {
        // Given: A task for a non-existent payment
        UUID nonExistentPaymentId = UUID.randomUUID();
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(nonExistentPaymentId);
        task.setRetryAttempt(0);

        // Create message and channel without Mockito
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setDeliveryTag(1L);
        Message message = new Message(new byte[0], messageProperties);

        AtomicBoolean acked = new AtomicBoolean(false);
        AtomicBoolean nacked = new AtomicBoolean(false);
        Channel channel = createTestChannel(acked, nacked);

        // When: Worker tries to process task for missing payment
        paymentWorker.receive(task, message, channel);

        // Then: Message should be NACKed for redelivery (though in practice this might be DLQ'd)
        assertFalse(acked.get(), "Channel should not ack the missing payment task");
        assertTrue(nacked.get(), "Channel should nack the missing payment task");
    }

    private Channel createTestChannel(AtomicBoolean acked, AtomicBoolean nacked) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                if ("basicAck".equals(method.getName())) {
                    acked.set(true);
                    return null;
                }
                if ("basicNack".equals(method.getName())) {
                    nacked.set(true);
                    return null;
                }
                if (method.getReturnType().isPrimitive()) {
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    if (method.getReturnType() == short.class) {
                        return (short) 0;
                    }
                    if (method.getReturnType() == byte.class) {
                        return (byte) 0;
                    }
                    if (method.getReturnType() == char.class) {
                        return (char) 0;
                    }
                    if (method.getReturnType() == float.class) {
                        return 0f;
                    }
                    if (method.getReturnType() == double.class) {
                        return 0d;
                    }
                }
                return null;
            }
        };
        return (Channel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Channel.class}, handler);
    }

    @TestConfiguration
    static class ExternalApiStubTestConfig {

        @Bean
        @Primary
        ExternalApiClient externalApiClient(RestTemplate restTemplate,
                                           CircuitBreakerConfig circuitBreakerConfig,
                                           RetryConfig retryConfig,
                                           @Value("${payment.api.base-url}") String baseUrl) {
            return new StubExternalApiClient(restTemplate, circuitBreakerConfig, retryConfig, baseUrl);
        }
    }

    static class StubExternalApiClient extends ExternalApiClient {

        private Function<Payment, ApiResponse> behavior = payment -> {
            throw new IllegalStateException("Behavior not configured");
        };

        public StubExternalApiClient(RestTemplate restTemplate,
                                     CircuitBreakerConfig circuitBreakerConfig,
                                     RetryConfig retryConfig,
                                     String baseUrl) {
            super(restTemplate, circuitBreakerConfig, retryConfig, baseUrl);
        }

        @Override
        public ApiResponse processPayment(Payment payment) {
            return behavior.apply(payment);
        }

        public void setBehavior(Function<Payment, ApiResponse> behavior) {
            this.behavior = behavior;
        }
    }
}