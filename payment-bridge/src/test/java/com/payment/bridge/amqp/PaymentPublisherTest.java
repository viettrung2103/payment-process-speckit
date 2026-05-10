package com.payment.bridge.amqp;

import com.payment.bridge.config.RabbitMQConfig;
import com.payment.bridge.model.MessageQueueTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PaymentPublisher paymentPublisher;

    @BeforeEach
    void setUp() {
        paymentPublisher = new PaymentPublisher(rabbitTemplate);
    }

    @Test
    void publishPaymentTask_shouldUsePaymentExchangeAndRoutingKey() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());

        paymentPublisher.publishPaymentTask(task);

        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq(RabbitMQConfig.PAYMENT_ROUTING_KEY),
            eq(task)
        );
    }

    @Test
    void publishPaymentTaskWithDelay_shouldSetMessageExpiration() {
        MessageQueueTask task = new MessageQueueTask();
        task.setPaymentId(UUID.randomUUID());

        paymentPublisher.publishPaymentTaskWithDelay(task, 1500L);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq(RabbitMQConfig.RETRY_ROUTING_KEY),
            eq(task),
            captor.capture()
        );

        Message originalMessage = new Message(new byte[0], new MessageProperties());
        Message processedMessage = captor.getValue().postProcessMessage(originalMessage);

        assertThat(processedMessage.getMessageProperties().getExpiration()).isEqualTo("1500");
    }
}
