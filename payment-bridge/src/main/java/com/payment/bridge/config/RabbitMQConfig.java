package com.payment.bridge.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@Profile("!integration")
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment-exchange";
    public static final String PAYMENT_QUEUE = "payment-processing";
    public static final String PAYMENT_ROUTING_KEY = "payment.process";
    public static final String RETRY_QUEUE = "payment-retry";
    public static final String RETRY_ROUTING_KEY = "payment.retry";
    public static final String DLQ_QUEUE = "dlq-payment-failed";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", "");
        arguments.put("x-dead-letter-routing-key", DLQ_QUEUE);
        return new Queue(PAYMENT_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Queue retryQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", PAYMENT_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", PAYMENT_ROUTING_KEY);
        arguments.put("x-message-ttl", 300000); // 5 minutes TTL for retry queue
        return new Queue(RETRY_QUEUE, true, false, false, arguments);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(DLQ_QUEUE, true, false, false);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Binding retryBinding(Queue retryQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(retryQueue).to(paymentExchange).with(RETRY_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public ConnectionFactory connectionFactory(RabbitProperties rabbitProperties) {
        String host = rabbitProperties.getHost();
        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        Integer port = rabbitProperties.getPort();
        if (port == null) {
            port = 5672;
        }

        CachingConnectionFactory factory = new CachingConnectionFactory(host, port);
        if (rabbitProperties.getUsername() != null) {
            factory.setUsername(rabbitProperties.getUsername());
        }
        if (rabbitProperties.getPassword() != null) {
            factory.setPassword(rabbitProperties.getPassword());
        }
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                                  RabbitProperties rabbitProperties,
                                                                                  Executor virtualThreadExecutor) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(10);
        factory.setMaxConcurrentConsumers(50);
        factory.setTaskExecutor(virtualThreadExecutor);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(rabbitProperties.getListener().getSimple().isAutoStartup());
        return factory;
    }
}
