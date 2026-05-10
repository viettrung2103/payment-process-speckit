package com.payment.mock;

import com.payment.mock.config.MockPaymentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockPaymentProperties.class)
public class MockPaymentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockPaymentApiApplication.class, args);
    }
}
