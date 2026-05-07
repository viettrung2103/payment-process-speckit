package com.payment.bridge.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment-exchange";
    public static final String PAYMENT_QUEUE = "payment-processing";
    public static final String PAYMENT_ROUTING_KEY = "payment.process";
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
    public Queue dlqQueue() {
        return new Queue(DLQ_QUEUE, true, false, false);
    }

    @Bean
    public Binding paymentBinding(Queue paymentQueue, DirectExchange paymentExchange) {
        return BindingBuilder.bind(paymentQueue).to(paymentExchange).with(PAYMENT_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(10);
        factory.setMaxConcurrentConsumers(50);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
