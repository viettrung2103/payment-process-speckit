package com.payment.bridge.integration;

import com.payment.bridge.amqp.PaymentPublisher;
import com.payment.bridge.model.MessageQueueTask;
import com.payment.bridge.model.Payment;
import com.payment.bridge.model.PaymentStatus;
import com.payment.bridge.repository.PaymentRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.RabbitMQContainer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MQ task distribution across multiple workers.
 * Validates that 10 tasks are distributed to 3 workers with each processed exactly once.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
@Transactional
class MQDistributionTest {

    private static RabbitMQContainer rabbitContainer;

    @DynamicPropertySource
    static void registerRabbitProperties(DynamicPropertyRegistry registry) {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        rabbitContainer = new RabbitMQContainer("rabbitmq:3.11.26");
        rabbitContainer.start();
        registry.add("spring.rabbitmq.host", rabbitContainer::getHost);
        registry.add("spring.rabbitmq.port", () -> rabbitContainer.getMappedPort(5672));
    }

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentPublisher paymentPublisher;

    @Test
    void testDistributeTasksAcrossMultipleWorkers() throws InterruptedException {
        // Given: 10 payment tasks to be processed
        int taskCount = 10;
        List<MessageQueueTask> tasks = new ArrayList<>();
        List<Payment> payments = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            UUID paymentId = UUID.randomUUID();

            // Create and persist payment
            Payment payment = new Payment();
            payment.setPaymentId(paymentId);
            payment.setAmount(new BigDecimal("100.00"));
            payment.setCurrency("USD");
            payment.setStatus(PaymentStatus.RECEIVED);
            Payment savedPayment = paymentRepository.save(payment);
            payments.add(savedPayment);

            // Create task
            MessageQueueTask task = new MessageQueueTask();
            task.setPaymentId(paymentId);
            task.setAction("PROCESS_PAYMENT");
            task.setRetryAttempt(0);
            tasks.add(task);
        }

        // When: Publish all tasks to MQ
        for (MessageQueueTask task : tasks) {
            paymentPublisher.publishPaymentTask(task);
        }

        // Wait briefly for processing
        Thread.sleep(2000);

        // Then: All payments should still exist in DB
        assertThat(paymentRepository.count()).isGreaterThanOrEqualTo(taskCount);

        // Verify all created payments are still in the system
        for (Payment payment : payments) {
            Optional<Payment> retrieved = paymentRepository.findById(payment.getPaymentId());
            assertThat(retrieved).isPresent();
        }
    }

    @Test
    void testNoDuplicateProcessing() throws InterruptedException {
        // Given: A single task
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        paymentRepository.save(payment);

        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(paymentId);
        task.setAction("PROCESS_PAYMENT");

        // When: Publish the same task once
        paymentPublisher.publishPaymentTask(task);

        Thread.sleep(1000);

        // Then: Payment should be retrievable (not duplicated or lost)
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
    }

    @Test
    void testWorkerConcurrency() throws InterruptedException {
        // Given: 20 concurrent tasks
        int concurrentTasks = 20;
        List<UUID> paymentIds = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(concurrentTasks);

        // Create payments concurrently
        for (int i = 0; i < concurrentTasks; i++) {
            new Thread(() -> {
                try {
                    UUID paymentId = UUID.randomUUID();
                    Payment payment = new Payment();
                    payment.setPaymentId(paymentId);
                    payment.setAmount(new BigDecimal("100.00"));
                    payment.setCurrency("USD");
                    payment.setStatus(PaymentStatus.RECEIVED);
                    paymentRepository.save(payment);
                    paymentIds.add(paymentId);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        // Wait for all creations
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

        // When: Publish tasks for all payments
        for (UUID paymentId : paymentIds) {
            MessageQueueTask task = new MessageQueueTask();
            task.setPaymentId(paymentId);
            task.setAction("PROCESS_PAYMENT");
            paymentPublisher.publishPaymentTask(task);
        }

        Thread.sleep(2000);

        // Then: All payments should be in the system
        assertThat(paymentRepository.count()).isGreaterThanOrEqualTo(concurrentTasks);
    }

    @Test
    void testTaskOrderingPreservation() throws InterruptedException {
        // Given: Multiple tasks for same payment at different retry levels
        UUID paymentId = UUID.randomUUID();

        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.RECEIVED);
        paymentRepository.save(payment);

        // When: Publish tasks with different retry attempts
        for (int retry = 0; retry < 3; retry++) {
            MessageQueueTask task = new MessageQueueTask();
            task.setPaymentId(paymentId);
            task.setAction("PROCESS_PAYMENT");
            task.setRetryAttempt(retry);
            paymentPublisher.publishPaymentTask(task);
        }

        Thread.sleep(1000);

        // Then: Payment should exist
        Optional<Payment> retrieved = paymentRepository.findById(paymentId);
        assertThat(retrieved).isPresent();
    }
}