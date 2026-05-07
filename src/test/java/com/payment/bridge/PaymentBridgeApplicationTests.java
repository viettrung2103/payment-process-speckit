package com.payment.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class PaymentBridgeApplicationTests {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void contextLoads() {
    }
}
